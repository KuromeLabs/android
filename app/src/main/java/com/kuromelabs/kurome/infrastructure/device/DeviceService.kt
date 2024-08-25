package com.kuromelabs.kurome.infrastructure.device

import Kurome.Fbs.Component
import Kurome.Fbs.DeviceIdentityResponse
import Kurome.Fbs.Packet
import Kurome.Fbs.Platform
import android.os.Environment
import android.os.StatFs
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.application.devices.Device
import com.kuromelabs.kurome.application.devices.DeviceRepository
import com.kuromelabs.kurome.application.interfaces.SecurityService
import com.kuromelabs.kurome.infrastructure.network.Link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.channels.Channels
import java.security.KeyPair
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class DeviceService(
    private var scope: CoroutineScope,
    private var identityProvider: IdentityProvider,
    private var securityService: SecurityService<X509Certificate, KeyPair>,
    private var deviceRepository: DeviceRepository,
) {
    private val _deviceHandles = ConcurrentHashMap<String, DeviceHandle>()

    private val _deviceStates = MutableStateFlow<MutableMap<String, DeviceState>>(mutableMapOf())
    val deviceStates = _deviceStates.asStateFlow()

    private val _savedDevices = deviceRepository.getSavedDevices()
        .map { devices -> devices.associateBy { it.id } }
        .stateIn(scope, SharingStarted.Eagerly, mutableMapOf())


    suspend fun handleUdp(name: String, id: String, ip: String, port: Int) {
        if (_deviceHandles.containsKey(id)) {
//            Timber.d("Device $id is already connected or connecting, ignoring")
            return
        }
        val device = _savedDevices.value[id]
        val deviceHandle =
            DeviceHandle(
                if (device?.certificate != null) PairStatus.PAIRED else PairStatus.UNPAIRED,
                name,
                id,
                null
            )
        _deviceHandles[id] = deviceHandle
        var sslSocket: SSLSocket?
        scope.launch {
            val socket = Socket()
            val result = try {
                socket.reuseAddress = true
                Timber.d("Connecting to socket $ip:$port")
                socket.connect(InetSocketAddress(ip, port), 3000)
                Timber.d("Sending identity to device $name:$id at $ip:$port")
                sendIdentity(socket)

                Timber.d("Upgrading $ip:$port to SSL")
                sslSocket = upgradeToSslSocket(socket, true, device)
                Result.success(sslSocket!!)
            } catch (e: Exception) {
                socket.close()
                Timber.e("Exception at handleUdp: $e")
                Result.failure(e)
            }
            handleConnection(result, deviceHandle, id, ip, port, name)

        }
    }

    private fun handleConnection(
        result: Result<SSLSocket>,
        deviceHandle: DeviceHandle,
        id: String,
        ip: String,
        port: Int,
        name: String
    ) {
        result.onSuccess { sslSocket ->
            Timber.d("Connected to $ip:$port, name: $name, id: $id")
            val link = Link(sslSocket, deviceHandle.localScope)
            deviceHandle.link = link
            deviceHandle.certificate = sslSocket.session.peerCertificates[0] as X509Certificate
            _deviceHandles[id] = deviceHandle
            deviceHandle.reloadPlugins(identityProvider)
            deviceHandle.localScope.launch(Dispatchers.Unconfined) {
            link.receivedPackets
                .filter { it.isFailure || (it.isSuccess && it.getOrNull()!!.componentType == Component.Pair) }
                .collect { packetResult ->
                    packetResult.onSuccess {
                        handleIncomingPairPacket(it.component(Kurome.Fbs.Pair()) as Kurome.Fbs.Pair, deviceHandle)
                    }
                    packetResult.onFailure { onDeviceDisconnected(deviceHandle.id) }
                }
            }
            deviceHandle.start()
            reloadDeviceStates()
        }
        result.onFailure {
            _deviceHandles[id]?.stop()
            onDeviceDisconnected(id)
            Timber.e("Failed to connect to $ip:$port")
        }
    }

    private suspend fun handleIncomingPairPacket(pair: Kurome.Fbs.Pair, handle: DeviceHandle) {
        if (pair.value) {
            when (handle.pairStatus) {
                PairStatus.PAIRED -> {
                    //TODO: handle. maybe unpair first?
                    Timber.d("Received incoming pair request, but already paired")
                }

                PairStatus.UNPAIRED -> {
                    //Incoming pair request. TODO: Implement UI to accept or reject
                    Timber.d("Received incoming pair request")
                    handle.pairStatus = PairStatus.PAIR_REQUESTED_BY_PEER
                }

                PairStatus.PAIR_REQUESTED -> {
                    //We made outgoing pair request and it was accepted
                    Timber.d("Outgoing pair request accepted by peer ${handle.id}, saving")
                    handle.pairStatus = PairStatus.PAIRED
                    handle.outgoingPairRequestTimerJob?.cancel()
                    deviceRepository.insert(Device(handle.id, handle.name, handle.certificate))
                    handle.reloadPlugins(identityProvider)
                    reloadDeviceStates()
                }

                else -> {
                    Timber.d("Received pair request, but status is ${handle.pairStatus}")
                }
            }
        } else {
            when (handle.pairStatus) {
                PairStatus.PAIR_REQUESTED -> {
                    //We made outgoing pair request and it was rejected
                    handle.pairStatus = PairStatus.UNPAIRED
                    handle.outgoingPairRequestTimerJob?.cancel()
                }

                PairStatus.PAIRED -> {
                    //Incoming unpair request. TODO: implement unpair
                }

                else -> {
                    Timber.d("Received unpair request, but status is ${handle.pairStatus}")
                }
            }
        }
    }

    private fun onDeviceDisconnected(id: String) {
        _deviceHandles.remove(id)?.stop()
        reloadDeviceStates()
    }

    private fun reloadDeviceStates() {
        _deviceStates.update { deviceStateMap ->
            deviceStateMap.toMutableMap().also {
                it.clear()
                _deviceHandles.forEach { handle ->
                    it[handle.value.id] = DeviceState(
                        handle.value.name,
                        handle.value.id,
                        handle.value.pairStatus,
                        true
                    )
                }
            }
        }
    }



    private fun upgradeToSslSocket(
        socket: Socket,
        clientMode: Boolean,
        device: Device?
    ): SSLSocket {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry(
            "key",
            securityService.getKeys()!!.private,
            "".toCharArray(),
            arrayOf(securityService.getSecurityContext())
        )

        if (device != null)
            keyStore.setCertificateEntry("cert", device.certificate)

        val keyManagerFactory =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, "".toCharArray())

        val trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        val sslContext = SSLContext.getInstance("TLSv1.2")
        if (device?.certificate == null) {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate?> {
                    return arrayOfNulls(0)
                }

                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            })
            sslContext.init(
                keyManagerFactory.keyManagers,
                trustAllCerts,
                java.security.SecureRandom()
            )
        }
        else
            sslContext.init(
                keyManagerFactory.keyManagers,
                trustManagerFactory.trustManagers,
                java.security.SecureRandom()
            )
        val sslSocket: SSLSocket = sslContext.socketFactory.createSocket(
            socket, socket.inetAddress.hostAddress, socket.port, true
        ) as SSLSocket

        if (clientMode) {
            sslSocket.useClientMode = true
        } else {
            sslSocket.useClientMode = false
            sslSocket.needClientAuth = false
            sslSocket.wantClientAuth = false
        }
        sslSocket.soTimeout = 3000
        sslSocket.startHandshake()
        sslSocket.soTimeout = 0
        return sslSocket
    }

    private fun sendIdentity(socket: Socket) {
        val builder = FlatBufferBuilder(256)

        val id = identityProvider.getEnvironmentId()
        val name = identityProvider.getEnvironmentName()
        val statFs = StatFs(Environment.getDataDirectory().path)

        val response = DeviceIdentityResponse.createDeviceIdentityResponse(
            builder,
            statFs.totalBytes,
            statFs.freeBytes,
            builder.createString(name),
            builder.createString(id),
            builder.createString(""),
            Platform.Android
        )

        val p = Packet.createPacket(builder, Component.DeviceIdentityResponse, response, -1)
        builder.finishSizePrefixed(p)
        val buffer = builder.dataBuffer()

        val channel = Channels.newChannel(socket.getOutputStream())
        channel.write(buffer)
    }

    fun onNetworkLost() {
        Timber.d("Network lost")
        _deviceHandles.values.forEach {
            it.stop()
        }
        _deviceHandles.clear()
        reloadDeviceStates()
    }

    suspend fun sendOutgoingPairRequest(id: String) {
        val deviceHandle = _deviceHandles[id] ?: return
        // send pair request with 30s timeout
        if (deviceHandle.pairStatus == PairStatus.PAIRED) {
            Timber.d("Already paired")
            return
        }
        if (deviceHandle.pairStatus == PairStatus.PAIR_REQUESTED) {
            Timber.d("Pair request already sent")
            return
        }
        deviceHandle.pairStatus = PairStatus.PAIR_REQUESTED

        deviceHandle.outgoingPairRequestTimerJob = scope.launch {
            delay(30000)
            Timber.d("Pair request timed out")
            deviceHandle.pairStatus = PairStatus.UNPAIRED
            coroutineContext.job.cancel()
        }
        val builder = FlatBufferBuilder(256)
        val pair = Kurome.Fbs.Pair.createPair(builder, true)
        val p = Packet.createPacket(builder, Component.Pair, pair, -1)
        builder.finishSizePrefixed(p)
        val buffer = builder.dataBuffer()
        deviceHandle.sendPacket(buffer)
    }

}


data class DeviceState(
    val name: String,
    val id: String,
    val pairStatus: PairStatus,
    val connected: Boolean
)
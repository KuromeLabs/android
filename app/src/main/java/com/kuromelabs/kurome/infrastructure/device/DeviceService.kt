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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    var deviceRepository: DeviceRepository,
) {
    private val _deviceHandlers = ConcurrentHashMap<String, DeviceHandler>()

    private val _deviceStates = MutableStateFlow<MutableMap<String, DeviceState>>(mutableMapOf())
    val deviceStates = _deviceStates.asStateFlow()

    private val _savedDevices = deviceRepository.getSavedDevices()
        .map { devices -> devices.associateBy { it.id } }
        .stateIn(scope, SharingStarted.Eagerly, mutableMapOf())


    suspend fun handleUdp(name: String, id: String, ip: String, port: Int) {
        if (_deviceHandlers.containsKey(id)) {
//            Timber.d("Device $id is already connected or connecting, ignoring")
            return
        }
        val device = _savedDevices.value[id]
        val deviceHandler =
            DeviceHandler(
                scope,
                identityProvider,
                device?.certificate != null,
                name,
                id,
                null,
                null
            )
        _deviceHandlers[id] = deviceHandler
        var sslSocket: SSLSocket? = null
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
                val link = Link(sslSocket!!, scope)
                Result.success(link)
            } catch (e: Exception) {
                socket.close()
                Timber.e("Exception at handleUdp: $e")
                Result.failure(e)
            }
            handleConnection(result, deviceHandler, id, ip, port, name, sslSocket)

        }
    }

    private suspend fun handleConnection(
        result: Result<Link>,
        deviceHandler: DeviceHandler,
        id: String,
        ip: String,
        port: Int,
        name: String,
        sslSocket: SSLSocket?
    ) {
        if (result.isSuccess) {
            Timber.d("Connected to $ip:$port, name: $name, id: $id")
            val link = result.getOrNull()!!
            deviceHandler.link = link
            deviceHandler.certificate = sslSocket!!.session.peerCertificates[0] as X509Certificate
            _deviceHandlers[id] = deviceHandler
            _deviceHandlers[id]!!.start()
            scope.launch {
                _deviceHandlers[id]!!.pairStatus.combine(link.isConnected) { pairStatus, connected ->
                    Pair(pairStatus, connected)
                }.collect { pair ->
                    val pairStatus = pair.first
                    val connected = pair.second
                    if (pairStatus == PairStatus.PAIRED && connected) {
                        deviceRepository.insert(Device(id, name, deviceHandler.certificate))
                    }
                    Timber.d("Collecting inside DeviceService. Status: ${pair.first.name}, Connected: ${pair.second}")
                    _deviceStates.update { deviceStateMap ->
                        deviceStateMap.toMutableMap().also {
                            it[id] = DeviceState(
                                name,
                                id,
                                pairStatus,
                                connected
                            )
                        }
                    }
                    if (!connected) {
                        onDeviceDisconnected(id)
                        coroutineContext.job.cancel()
                        return@collect
                    }
                }
            }

        } else {
            _deviceHandlers[id]?.stop()
            onDeviceDisconnected(id)
            Timber.e("Failed to connect to $ip:$port")
        }
    }

    private fun onDeviceDisconnected(id: String) {
        _deviceHandlers.remove(id)
        _deviceStates.update { deviceStateMap ->
            deviceStateMap.toMutableMap().also {
                it.remove(id)
            }
        }
    }


    private fun upgradeToSslSocket(
        socket: Socket,
        clientMode: Boolean,
        device: Device?
    ): SSLSocket {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate?> {
                return arrayOfNulls(0)
            }

            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        })


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
        if (device?.certificate == null)
            sslContext.init(
                keyManagerFactory.keyManagers,
                trustAllCerts,
                java.security.SecureRandom()
            )
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
        _deviceHandlers.values.forEach { it.onNetworkLost() }
        _deviceHandlers.clear()
    }

    suspend fun sendOutgoingPairRequest(id: String) {
        val deviceHandler = _deviceHandlers[id] ?: return
        deviceHandler.sendOutgoingPairRequest()
    }

}


data class DeviceState(
    val name: String,
    val id: String,
    val pairStatus: PairStatus,
    val connected: Boolean
)
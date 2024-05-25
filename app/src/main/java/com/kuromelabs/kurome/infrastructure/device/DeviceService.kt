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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val _deviceStates = MutableStateFlow<MutableMap<String, DeviceState>>(mutableMapOf())
    val deviceStates: StateFlow<MutableMap<String, DeviceState>> = _deviceStates

    suspend fun handleUdp(name: String, id: String, ip: String, port: Int) {
        if (_deviceStates.value.containsKey(id)) {
//            Timber.d("Device $id is already connected or connecting, ignoring")
            return
        }
        var device = deviceRepository.getSavedDevice(id)
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

            if (result.isSuccess) {
                Timber.d("Connected to $ip:$port, name: $name, id: $id")
                val link = result.getOrNull()!!
                _deviceStates.update { states ->
                    states.toMutableMap().also {
                        it[id] = DeviceState(
                            device ?: Device(id, name, sslSocket!!.session.peerCertificates[0] as X509Certificate?),
                            if (device != null) DeviceState.Status.PAIRED else DeviceState.Status.UNPAIRED,
                            link
                        ).apply { statusMessage = "Connected" }
                    }
                }

                val packetHandler = DevicePacketHandler(link, scope, identityProvider)
                _deviceStates.value[id]!!.devicePacketHandler = packetHandler
                _deviceStates.value[id]!!.start()
                val pairPacketJob = launch {
                    link.receivedPackets.collect { packet ->
                        if (packet.componentType == Component.Pair) {
                            _deviceStates.value[id]?.let { handleIncomingPairPacket(packet, it) }
                        }
                    }
                }
                launch {
                    link.isConnected.collect { connected ->
                        Timber.d("Link connected status: $connected")
                        if (!connected) {
                            _deviceStates.value[id]?.stop()
                            _deviceStates.update { states ->
                                states.toMutableMap().apply { remove(id) }
                            }
                            pairPacketJob.cancel()
                            coroutineContext.job.cancel()
                        }
                    }
                }

            } else {
                _deviceStates.value[id]?.stop()
                _deviceStates.update { states -> states.toMutableMap().apply { remove(id) } }
                Timber.e("Failed to connect to $ip:$port")
            }
        }
    }


    private suspend fun handleIncomingPairPacket(packet: Packet, state: DeviceState) {
        val pair = packet.component(Kurome.Fbs.Pair()) as Kurome.Fbs.Pair
        if (pair.value) {
            when (state.status) {
                DeviceState.Status.PAIRED -> {
                    //TODO: handle. maybe unpair first?
                    Timber.d("Received incoming pair request, but already paired")
                }

                DeviceState.Status.UNPAIRED -> {
                    //Incoming pair request. TODO: Implement UI to accept or reject
                    Timber.d("Received incoming pair request")
                    state.status = DeviceState.Status.PAIR_REQUESTED_BY_PEER
                    _deviceStates.update { states ->
                        states.toMutableMap().apply { put(state.device.id, state) }
                    }
                }

                DeviceState.Status.PAIR_REQUESTED -> {
                    //We made outgoing pair request and it was accepted
                    Timber.d("Outgoing pair request accepted by peer ${state.device.id}, saving")
                    val cert = state.device.certificate!!
                    Timber.d(cert.toString())
                    deviceRepository.insert(state.device)
                    state.status = DeviceState.Status.PAIRED
                    state.outgoingPairRequestTimerJob?.cancel()
                    _deviceStates.update { states ->
                        states.toMutableMap().apply { put(state.device.id, state) }
                    }
                }

                else -> {
                    Timber.d("Received pair request, but status is ${state.status}")
                }
            }
        } else {
            when (state.status) {
                DeviceState.Status.PAIR_REQUESTED -> {
                    //We made outgoing pair request and it was rejected
                    state.status = DeviceState.Status.UNPAIRED
                    state.outgoingPairRequestTimerJob?.cancel()
                    _deviceStates.update { states ->
                        states.toMutableMap().apply { put(state.device.id, state) }
                    }
                }

                DeviceState.Status.PAIRED -> {
                    //Incoming unpair request. TODO: implement unpair

                }

                else -> {
                    Timber.d("Received unpair request, but status is ${state.status}")
                }
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
        if (device == null)
            sslContext.init(keyManagerFactory.keyManagers, trustAllCerts, java.security.SecureRandom())
        else
            sslContext.init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, java.security.SecureRandom())
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
        _deviceStates.value.forEach { (_, state) -> state.stop() }
        _deviceStates.update { mutableMapOf() }
    }

    fun sendOutgoingPairRequest(id: String) {
        Timber.d("Sending pair request to $id")
        val deviceState = _deviceStates.value[id] ?: return
        // send pair request with 30s timeout
        if (deviceState.status == DeviceState.Status.PAIRED) {
            Timber.d("Already paired")
            return
        }
        if (deviceState.status == DeviceState.Status.PAIR_REQUESTED) {
            Timber.d("Pair request already sent")
            return
        }
        deviceState.status = DeviceState.Status.PAIR_REQUESTED


        deviceState.outgoingPairRequestTimerJob = scope.launch {
            delay(30000)
            Timber.d("Pair request timed out")
            deviceState.status = DeviceState.Status.UNPAIRED
            _deviceStates.update { states ->
                states.toMutableMap().apply { put(id, deviceState) }
            }
            coroutineContext.job.cancel()

        }
        val builder = FlatBufferBuilder(256)
        val pair = Kurome.Fbs.Pair.createPair(builder, true)
        val p = Packet.createPacket(builder, Component.Pair, pair, -1)
        builder.finishSizePrefixed(p)
        val buffer = builder.dataBuffer()
        deviceState.link?.send(buffer)
        _deviceStates.update { states ->
            states.toMutableMap().apply { put(id, deviceState) }
        }
    }

}


class DeviceState(
    var device: Device,
    var status: Status,
    var link: Link?
) {
    var isConnected: Boolean = false
    var statusMessage: String = ""
    var devicePacketHandler: DevicePacketHandler? = null
    var outgoingPairRequestTimerJob: Job? = null

    enum class Status {
        PAIRED, UNPAIRED, PAIR_REQUESTED, PAIR_REQUESTED_BY_PEER
    }

    fun stop() {
        devicePacketHandler?.stopHandling()
        link?.close()
    }

    fun start() {
        link?.start()
        devicePacketHandler?.startHandling()
    }
}
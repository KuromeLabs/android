package com.kuromelabs.kurome.infrastructure.device

import Kurome.Fbs.Component
import Kurome.Fbs.DeviceQueryResponse
import Kurome.Fbs.Packet
import android.os.Environment
import android.os.StatFs
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.application.devices.Device
import com.kuromelabs.kurome.application.devices.DeviceRepository
import com.kuromelabs.kurome.application.interfaces.SecurityService
import com.kuromelabs.kurome.infrastructure.network.Link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
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
import javax.net.ssl.X509TrustManager

class DeviceService(
    var scope: CoroutineScope,
    var identityProvider: IdentityProvider,
    var securityService: SecurityService<X509Certificate, KeyPair>,
    var deviceRepository: DeviceRepository,
) {
    // flow of device states
    private val _deviceStates = MutableStateFlow<MutableMap<String, DeviceState>>(mutableMapOf())
    val deviceStates: StateFlow<MutableMap<String, DeviceState>> = _deviceStates

    private val _deviceContexts: MutableMap<String, DeviceContext> = mutableMapOf()

    fun handleUdp(name: String, id: String, ip: String, port: Int) {
        if (_deviceStates.value.containsKey(id)) {
            Timber.d("Device $id is already connected or connecting, ignoring")
            return
        }
        val device = Device(id, name)
        _deviceStates.update { states ->
            states.toMutableMap().also {
                it[id] = DeviceState(device, DeviceState.Status.CONNECTING)
            }
        }
        scope.launch {
            val socket = Socket()
            val result = try {
                socket.reuseAddress = true
                Timber.d("Connecting to socket $ip:$port")
                socket.connect(InetSocketAddress(ip, port))
                Timber.d("Sending identity to device $name:$id at $ip:$port")
                sendIdentity(socket, ip, port)

                Timber.d("Upgrading $ip:$port to SSL")
                val sslSocket = upgradeToSslSocket(socket, true)

                val link = Link(sslSocket, scope)
                Result.success(link)
            } catch (e: Exception) {
                socket.close()
                Timber.e("Exception at handleUdp: $e")
                Result.failure(e)
            }

            if (result.isSuccess) {
                Timber.d("Connected to $ip:$port, name: $name, id: $id")
                val link = result.getOrNull()!!
                _deviceContexts[id] =
                    DeviceContext(DevicePacketHandler(link, scope, identityProvider), link)
                _deviceContexts[id]!!.start()

                launch {
                    link.isConnected.collect { connected ->
                        Timber.d("Link connected status: $connected")
                        if (!connected) {
                            _deviceContexts[id]?.stop()
                            _deviceContexts.remove(id)
                            _deviceStates.update { states ->
                                states.toMutableMap().apply { remove(id) }
                            }
                            currentCoroutineContext().job.cancel()
                        } else {
                            _deviceStates.update { states ->
                                states.toMutableMap().apply {
                                    put(id, DeviceState(device, DeviceState.Status.CONNECTED_TRUSTED))
                                }
                            }
                        }
                    }
                }
            } else {
                _deviceContexts[id]?.stop()
                _deviceContexts.remove(id)
                _deviceStates.update { states -> states.toMutableMap().apply { remove(id) } }
                Timber.e("Failed to connect to $ip:$port")
            }
        }
    }

    private fun upgradeToSslSocket(socket: Socket, clientMode: Boolean): SSLSocket {
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

        val keyManagerFactory =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, "".toCharArray())


        val sslContext = SSLContext.getInstance("TLSv1.2")
        sslContext.init(keyManagerFactory.keyManagers, trustAllCerts, java.security.SecureRandom())
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
        sslSocket.startHandshake()
        return sslSocket
    }

    private fun sendIdentity(socket: Socket, ip: String, port: Int) {
        val builder = FlatBufferBuilder(256)

        val id = identityProvider.getEnvironmentId()
        val name = identityProvider.getEnvironmentName()
        val statFs = StatFs(Environment.getDataDirectory().path)

        val response = DeviceQueryResponse.createDeviceQueryResponse(
            builder,
            statFs.totalBytes,
            statFs.freeBytes,
            builder.createString(name),
            builder.createString(id),
            builder.createString("")
        )

        val p = Packet.createPacket(builder, Component.DeviceQueryResponse, response, -1)
        builder.finishSizePrefixed(p)
        val buffer = builder.dataBuffer()

        val channel = Channels.newChannel(socket.getOutputStream())
        channel.write(buffer)
    }

    fun onNetworkLost() {
        Timber.d("Network lost")
        _deviceContexts.forEach { (_, context) -> context.stop() }
        _deviceContexts.clear()
        _deviceStates.update { mutableMapOf() }
    }

    private class DeviceContext(var devicePacketHandler: DevicePacketHandler, var link: Link) {
        fun stop() {
            devicePacketHandler.stopHandling()
            link.close()
        }

        fun start() {
            link.start()
            devicePacketHandler.startHandling()
        }
    }
}


class DeviceState(var device: Device, var status: Status) {
    enum class Status {
        DISCONNECTED, CONNECTING, CONNECTED_TRUSTED, CONNECTED_UNTRUSTED
    }

    fun isConnectedOrConnecting(): Boolean {
        return status == Status.CONNECTING || status == Status.CONNECTED_TRUSTED || status == Status.CONNECTED_UNTRUSTED
    }
}
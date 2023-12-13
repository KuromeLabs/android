package com.kuromelabs.kurome.infrastructure.network

import android.content.Context
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.application.interfaces.DeviceRepository
import com.kuromelabs.kurome.application.interfaces.SecurityService
import com.kuromelabs.kurome.domain.Device
import com.kuromelabs.kurome.infrastructure.device.IdentityProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kurome.fbs.Component
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
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

class LinkProvider(
    var securityService: SecurityService<X509Certificate, KeyPair>,
    var scope: CoroutineScope,
    var identityProvider: IdentityProvider,
    var context: Context,
    var deviceRepository: DeviceRepository
) {
    private val udpListenPort = 33586
    private val devicesConnectedOrConnectingSet = HashSet<String>()

    private fun createClientLink(name: String, id: String, ip: String, port: Int): Link {

        val socket = Socket()
        socket.reuseAddress = true
        sendIdentity(socket, ip, port)
        Timber.d("Sent identity")

        val sslSocket = upgradeToSslSocket(socket, true)

        return Link(sslSocket, scope)

    }

    fun start() {
        startUdpListener()
    }

    suspend fun createServerLink(client: Socket): Link {
        TODO("Not yet implemented")
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
        val identity = flatBufferHelper.createDeviceInfoResponse(
            builder,
            identityProvider.getEnvironmentId(),
            identityProvider.getEnvironmentName()
        )
        val packet = flatBufferHelper.createPacket(builder, identity, Component.DeviceResponse, -1)
        val buffer = flatBufferHelper.finishBuilding(builder, packet)
        socket.connect(InetSocketAddress(ip, port))
        val channel = Channels.newChannel(socket.getOutputStream())
        channel.write(buffer)
    }


    private fun startUdpListener() {
        scope.launch {


            val udpSocket = DatagramSocket(null)
            Timber.d("Setting up socket")
            udpSocket.reuseAddress = true
            udpSocket.broadcast = true
            udpSocket.bind(InetSocketAddress(udpListenPort))
            Timber.d("Socket bound to port $udpListenPort")
            while (scope.coroutineContext.isActive && !udpSocket.isClosed) {
                try {
                    if (!udpSocket.isClosed) {
                        val buffer = ByteArray(1024)
                        val packet = DatagramPacket(buffer, buffer.size)
                        Timber.d("Waiting for incoming UDP")
                        udpSocket.receive(packet)
                        val message = String(packet.data, packet.offset, packet.length)
                        Timber.d("received UDP: $message")
                        val strs = message.split(':')
                        val id = strs[3]
                        val ip = strs[1]
                        val name = strs[2]
                        Timber.d("Current connected IDs: $devicesConnectedOrConnectingSet")
                        if (devicesConnectedOrConnectingSet.contains(id)) continue
                        devicesConnectedOrConnectingSet.add(id)
                        handleClientConnection(
                            name,
                            id,
                            ip,
                            33587
                        )

                    }
                } catch (e: Exception) {
                    Timber.d("Exception at UdpListenerService: $e")
                }
            }
            udpSocket.close()

        }
    }

    private suspend fun handleClientConnection(name: String, id: String, ip: String, port: Int) {
        val result = try {
            val link = createClientLink(name, id, ip, port)
            Result.success(link)
        } catch (e: Exception) {
            Result.failure(e)
        }

        if (result.isSuccess) {
            Timber.d("Connected to $ip:$port, name: $name, id: $id")
            val link = result.getOrNull()!!
            var device = deviceRepository.getSavedDevice(id)
            if (device == null) device = Device(id, name)
            link.setDevice(device)
            device.connect(link)
            link.start()
            scope.launch {
                link.isConnected.collect {
                    Timber.d("Link connected status: $it")
                    if (!it) {
                        deviceRepository.removeActiveDevice(device)
                        devicesConnectedOrConnectingSet.remove(id)
                        currentCoroutineContext().job.cancel()
                    } else {
                        deviceRepository.addActiveDevice(device)
                    }
                }
            }
        } else {
            Timber.e("Failed to connect to $ip:$port")
        }
    }
}
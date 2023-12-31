package com.kuromelabs.kurome.infrastructure.network

import Kurome.Fbs.Component
import Kurome.Fbs.DeviceQuery
import Kurome.Fbs.DeviceQueryResponse
import Kurome.Fbs.Packet
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Environment
import android.os.StatFs
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
    var context: Context,
    var deviceRepository: DeviceRepository
) {
    private val udpListenPort = 33586

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val identityProvider = IdentityProvider(context)


    fun registerNetworkCallback(networkCallback: ConnectivityManager.NetworkCallback) {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()


        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

    }

    fun unregisterNetworkCallback(networkCallback: ConnectivityManager.NetworkCallback) {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            // Called when a network is available

        }

        override fun onLost(network: Network) {
            // Called when a network is lost
            val devices = deviceRepository.getActiveDevices().value
            for (device in devices) {
                device.value.disconnect()
            }
        }
    }

    fun start() {
        registerNetworkCallback(networkCallback)
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
//                        Timber.d("Waiting for incoming UDP")
                        udpSocket.receive(packet)
                        val message = String(packet.data, packet.offset, packet.length)
                        Timber.d("received UDP: $message")
                        val strs = message.split(':')
                        val id = strs[3]
                        val ip = strs[1]
                        val name = strs[2]
                        if (deviceRepository.getActiveDevices().value.contains(id)) continue
                        var device = deviceRepository.getSavedDevice(id)
                        if (device == null) device = Device(id, name)
                        deviceRepository.addActiveDevice(device)

                        handleClientConnection(
                            name,
                            id,
                            ip,
                            33587,
                            device
                        )

                    }
                } catch (e: Exception) {
                    Timber.d("Exception at UdpListenerService: $e")
                }
            }
            udpSocket.close()

        }
    }

    private suspend fun handleClientConnection(name: String, id: String, ip: String, port: Int, device: Device) {
        val socket = Socket()
        val result = try {
            socket.reuseAddress = true
            sendIdentity(socket, ip, port)
            Timber.d("Sent identity")

            val sslSocket = upgradeToSslSocket(socket, true)

            val link = Link(sslSocket, scope)
            Result.success(link)
        } catch (e: Exception) {
            socket.close()
            Timber.e("Exception at handleClientConnection: $e")
            Result.failure(e)
        }

        if (result.isSuccess) {
            Timber.d("Connected to $ip:$port, name: $name, id: $id")
            val link = result.getOrNull()!!

            device.connect(link, scope, context)
            val linkJob = scope.launch { link.start() }
            deviceRepository.addActiveDevice(device)
            scope.launch {
                link.isConnected.collect {
                    Timber.d("Link connected status: $it")
                    if (!it) {
                        deviceRepository.removeActiveDevice(device)
                        linkJob.cancel()
                        currentCoroutineContext().job.cancel()
                    } else {
                        deviceRepository.addActiveDevice(device)
                    }
                }
            }
        } else {
            deviceRepository.removeActiveDevice(device)
            Timber.e("Failed to connect to $ip:$port")
        }
    }
}
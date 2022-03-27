package com.kuromelabs.kurome.domain.util.link

import android.content.Context
import android.os.Build
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.domain.util.IdentityProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kurome.Action
import kurome.DeviceInfo
import kurome.Packet
import timber.log.Timber
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.*


@OptIn(ExperimentalUnsignedTypes::class)
@Suppress("BlockingMethodInNonBlockingContext")
class LinkProvider(val context: Context) {
    private var udpSocket: DatagramSocket? = null
    private val builder = FlatBufferBuilder(128)
    private val activeLinks = ConcurrentHashMap<String, Link>()
    private val udpIp = "255.255.255.255"
    private val udpListenPort = 33586
    private val udpSendPort = 33588
    private val tcpPort = 33587
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var listenJob: Job? = null
    private var castJob: Job? = null
    private val _linkFlow = MutableSharedFlow<LinkState>(0)

    init {
        startUdpListener()
        SslHelper.initializeSsl(context)
        startTcpListener()
        castUdp()
    }


    private fun startTcpListener() {
        scope.launch {
            val tcpListener = ServerSocket(tcpPort)
            while (currentCoroutineContext().isActive) {
                val socket = tcpListener.accept()
                Timber.d("Incoming connection from ${socket.inetAddress}")
                val inputStream = socket.getInputStream()
                val sizeBytes = ByteArray(4)
                var readSoFar = 0
                while (readSoFar != 4) readSoFar += inputStream.read(
                    sizeBytes, readSoFar, 4 - readSoFar
                )
                readSoFar = 0
                val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
                val buffer = ByteArray(size)
                Timber.d("Reading buffer of size $size")
                while (readSoFar != size) readSoFar += inputStream.read(
                    buffer, readSoFar, size - readSoFar
                )
                val packet = Packet.getRootAsPacket(ByteBuffer.wrap(buffer))
                val deviceInfo = packet.deviceInfo!!
                val id = deviceInfo.id!!
                val name = deviceInfo.name!!
                Timber.d("Received packet from $name with id $id")
                val link = createLink(socket, id, name, false)
                observeLinkState(link)

            }
        }


    }

    private fun castUdp() {
        castJob?.cancel()
        castJob = scope.launch {
            var succeeded = false
            var tries = 0
            while (!succeeded && currentCoroutineContext().isActive && tries < 3) {
                try {
                    tries++
                    val socket = DatagramSocket(null)
                    socket.reuseAddress = true
                    socket.broadcast = true
                    socket.bind(InetSocketAddress(udpSendPort))
                    val message =
                        "kurome:${getIPAddress()}:${Build.MODEL}:${IdentityProvider.getGuid(context)}"
                    val bytes = message.toByteArray()
                    val packet =
                        DatagramPacket(bytes, bytes.size, InetSocketAddress(udpIp, udpSendPort))
                    socket.send(packet)
                    succeeded = true
                } catch (e: Exception) {
                    Timber.e("Exception at castUDP (Attempt ${tries + 1}/3): $e")
                    delay(5000)
                }
            }
        }
    }

    private fun setUdpListener() {
        try {
            Timber.d("Setting up socket")
            udpSocket?.close()
            udpSocket = DatagramSocket(null)
            udpSocket?.reuseAddress = true
            udpSocket?.broadcast = true
            udpSocket?.bind(InetSocketAddress(udpListenPort))
        } catch (e: Exception) {
            Timber.d("Exception at setUdpListener: $e")
        }
    }

    private fun startUdpListener() {
        setUdpListener()
        listenJob?.cancel()
        listenJob = scope.launch(Dispatchers.IO) {
            Timber.d("initializing udp listener at $udpIp:$udpListenPort")
            while (currentCoroutineContext().isActive) {
                if (udpSocket != null && !udpSocket!!.isClosed) try {
                    val buffer = ByteArray(1024)
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpSocket!!.receive(packet)
                    val message = String(packet.data, packet.offset, packet.length)
                    Timber.d("received UDP: $message")
                    launch { datagramPacketReceived(packet) }
                } catch (e: Exception) {
                    Timber.d("Exception at initializeUdpListener: $e")
                }
            }
        }
    }

    @ExperimentalUnsignedTypes
    private fun datagramPacketReceived(packet: DatagramPacket) {
        try {
            val packetString = String(packet.data, packet.offset, packet.length)
            val split = packetString.split(':')
            val ip = split[1]
            val name = split[2]
            val id = split[3]
            Timber.d("Received UDP. name: $name, id: $id")
            if (activeLinks.containsKey(id)) {
                Timber.d("Link already exists, not connecting")
                return
            }
            val socket = Socket()
            socket.reuseAddress = true
            Timber.d("Sending TCP plaintext identity to $packetString")
            sendIdentity(socket, ip)
            val link = createLink(socket, id, name, true)
            Timber.d("Link connection started from UDP")
            observeLinkState(link)

        } catch (e: Exception) {
            Timber.e("Exception at datagramPacketReceived: $e")
        }

    }

    private fun sendIdentity(socket: Socket, ip: String) {
        val modelOffset = builder.createString(Build.MODEL)
        val guidOffset = builder.createString(IdentityProvider.getGuid(context))

        val info = DeviceInfo.createDeviceInfo(builder, modelOffset, guidOffset, 0, 0, 0)
        val result = Packet.createPacket(builder, 0, Action.actionConnect, 0, info, 0, 0, 0, 0)
        builder.finishSizePrefixed(result)

        socket.connect(InetSocketAddress(ip, tcpPort))
        val channel = Channels.newChannel(socket.getOutputStream())
        channel.write(builder.dataBuffer())
        Timber.d("Sent identity packet. Size: ${builder.dataBuffer().capacity()}")
        builder.clear()
    }

    private fun createLink(socket: Socket, id: String, name: String, clientMode: Boolean): Link {
        Timber.d("createLink called")
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
            "key", SslHelper.privateKey, "".toCharArray(), arrayOf(SslHelper.certificate)
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


        try {
            sslSocket.startHandshake()
            Timber.d("Handshake complete")
        } catch (e: Exception) {
            Timber.e("Handshake failed: $e")
        }




        return Link(id, name, sslSocket)
    }

    private fun observeLinkState(link: Link) {
        var linkJob: Job? = null
        linkJob = scope.launch {
            link.observeState().collect {
                when (it.state) {
                    LinkState.State.CONNECTED -> {
                        _linkFlow.emit(LinkState(LinkState.State.CONNECTED, link))
                        activeLinks[link.deviceId] = link
                    }
                    LinkState.State.DISCONNECTED -> {
                        _linkFlow.emit(LinkState(LinkState.State.DISCONNECTED, link))
                        activeLinks.remove(link.deviceId)
                        linkJob?.cancel()
                        if (!linkJob!!.isActive) Timber.e("linkJob was cancelled in linkDisconnected")
                    }
                }
            }
        }
    }

    fun observeLinks(): Flow<LinkState> = _linkFlow

    fun onStop() {
        listenJob?.cancel()
        udpSocket?.close()
    }

    private fun getIPAddress(): String {
        try {
            val interfaces: List<NetworkInterface> =
                NetworkInterface.getNetworkInterfaces().toList()
            for (networkInterface in interfaces) {
                val addrs: List<InetAddress> = networkInterface.inetAddresses.toList()
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val address = addr.hostAddress
                        val isIPv4 = address?.indexOf(':')!! < 0
                        if (isIPv4) return address
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e("Exception at getIPAddress: $e")
        }
        return ""
    }
}
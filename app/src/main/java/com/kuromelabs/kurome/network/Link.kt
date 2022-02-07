package com.kuromelabs.kurome.network

import com.google.flatbuffers.FlatBufferBuilder
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kurome.Packet
import timber.log.Timber
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


@OptIn(ExperimentalUnsignedTypes::class)
class Link(var deviceId: String, provider: LinkProvider) {
    private val selector: ActorSelectorManager = ActorSelectorManager(Dispatchers.IO)
    private val socketBuilder = aSocket(selector).tcp()
    private var clientSocket: Socket? = null
    private var out: ByteWriteChannel? = null
    private var `in`: ByteReadChannel? = null
    var builder = FlatBufferBuilder(1024)

    interface LinkDisconnectedCallback {
        fun onLinkDisconnected(link: Link);
    }

    interface PacketReceivedCallback {
        fun onPacketReceived(packet: Packet)
    }

    private var callback: LinkDisconnectedCallback = provider
    private var packetCallbacks = CopyOnWriteArrayList<PacketReceivedCallback>()
    private val sizeBytes = ByteArray(4)
    private var size = 0
    private var buffer = ByteArray(1024)

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    suspend fun startConnection(ip: String, port: Int) {

        //Setup SSL
        //TODO: Temporary, we should trust the server's certificate when pairing
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate?> {
                return arrayOfNulls(0)
            }

            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        }
        )
        val tlsConfigBuilder = TLSConfigBuilder()
        tlsConfigBuilder.trustManager = trustAllCerts[0]
        val tlsConfig = tlsConfigBuilder.build()

        clientSocket = socketBuilder.connect(InetSocketAddress(ip, port)).tls(
            Dispatchers.IO, tlsConfig
        )
        Timber.d("Link connected at $ip:$port")
        out = clientSocket!!.openWriteChannel(true)
        `in` = (clientSocket!!.openReadChannel())

        scope.launch {
            while (true) {
                Timber.d("Reading from socket")
                try {
                    `in`?.readFully(sizeBytes)
                    size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
                    if (size > buffer.size) {
                        buffer = ByteArray(size)
                    }
                    `in`?.readFully(buffer, 0, size)

                } catch (e: Exception) {
                    Timber.e("died at startConnection: $e")
                    stopConnection()
                    break
                }
                val packet = Packet.getRootAsPacket(ByteBuffer.wrap(buffer))
                packetReceived(packet)

            }
        }

    }

    private val mutex = Mutex()
     suspend fun sendByteBuffer(packet: ByteBuffer) {
        try {
            mutex.withLock {
                `out`?.writeFully(packet)
            }
        } catch (e: Exception) {
            Timber.e("died at sendByteBuffer: $e")
            stopConnection()
        }
    }

    private fun packetReceived(packet: Packet) {
//        Timber.d("Called packetReceived. Size of callbacks: ${packetCallbacks.size}")
        packetCallbacks.forEach {
            it.onPacketReceived(packet)
        }
    }

    fun addPacketCallback(callback: PacketReceivedCallback) {
        packetCallbacks.add(callback)
    }

    fun removePacketCallback(callback: PacketReceivedCallback) {
        packetCallbacks.remove(callback)
    }

    private fun stopConnection() {
        Timber.d("Stopping connection: $deviceId")
        packetCallbacks.clear()
        callback.onLinkDisconnected(this)
        `in`?.cancel()
        out?.close()
        clientSocket?.close()
//        scope.cancel()
    }

}
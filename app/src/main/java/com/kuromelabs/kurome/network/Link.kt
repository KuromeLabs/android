package com.kuromelabs.kurome.network

import com.google.flatbuffers.FlatBufferBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kurome.Packet
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


@OptIn(ExperimentalUnsignedTypes::class)
class Link(var deviceId: String, provider: LinkProvider) {
    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream
    private lateinit var clientSocket: Socket
    private lateinit var outputChannel: WritableByteChannel
    var builder = FlatBufferBuilder(1024)

    interface LinkDisconnectedCallback {
        fun onLinkDisconnected(link: Link)
    }


    private var callback: LinkDisconnectedCallback = provider
    private val sizeBytes = ByteArray(4)
    private var size = 0
    private var buffer = ByteArray(1024)

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val _packetFlow = MutableSharedFlow<Packet>(0)
    val packetFlow : SharedFlow<Packet> = _packetFlow

    @Suppress("BlockingMethodInNonBlockingContext")
    fun startConnection(ip: String, port: Int) {
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


        val sslContext = SSLContext.getInstance("TLSv1.2")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        val sslSocket: SSLSocket = sslContext.socketFactory.createSocket(ip, port) as SSLSocket
        sslSocket.startHandshake()
        clientSocket = sslSocket
        outputStream = clientSocket.getOutputStream()
        outputChannel = Channels.newChannel(outputStream)
        inputStream = clientSocket.getInputStream()
        Timber.d("Link connected at $ip:$port")


        scope.launch {
            while (true) {
//                Timber.d("Reading from socket")
                try {
                    var readSoFar = 0
                    while (readSoFar != 4)
                        readSoFar += inputStream.read(sizeBytes, readSoFar, 4 - readSoFar)
                    readSoFar = 0
                    size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
                    if (size > buffer.size) {
                        buffer = ByteArray(size)
                    }
                    while (readSoFar != size)
                        readSoFar += inputStream.read(buffer, readSoFar, size - readSoFar)

                } catch (e: Exception) {
                    Timber.e("died at startConnection: $e")
                    stopConnection()
                    break
                }
                val packet = Packet.getRootAsPacket(ByteBuffer.wrap(buffer))
                _packetFlow.emit(packet)

            }
        }

    }


    fun sendByteBuffer(buffer: ByteBuffer) {
        try {
            outputChannel.write(buffer)
//            Timber.d("Sent buffer")
        } catch (e: Exception) {
            Timber.e("died at sendByteBuffer: $e")
            stopConnection()
        }
    }

    private fun stopConnection() {
        Timber.d("Stopping connection: $deviceId")
        callback.onLinkDisconnected(this)
        clientSocket.close()
        scope.cancel()
    }

}
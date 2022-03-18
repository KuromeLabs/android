package com.kuromelabs.kurome.domain.util.link

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
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
class Link(val deviceId: String, val deviceName: String, val ip: String, val port: Int) {
    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream
    private var clientSocket: Socket? = null
    private lateinit var outputChannel: WritableByteChannel


    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val _packetFlow = MutableSharedFlow<Packet>(0)
    val packetFlow: SharedFlow<Packet> = _packetFlow

    private val _stateFlow = MutableSharedFlow<LinkState>(1)

    init {
        startConnection()
    }

    private fun startConnection() {
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
        outputStream = (clientSocket as SSLSocket).outputStream
        outputChannel = Channels.newChannel(outputStream)
        inputStream = (clientSocket as SSLSocket).inputStream
        Timber.d("Link connected at $ip:$port")
        startListening()
        scope.launch { _stateFlow.emit(LinkState(LinkState.State.CONNECTED, this@Link)) }
    }

    private fun startListening() {
        scope.launch {
            while (true) {
                try {
                    val sizeBytes = ByteArray(4)
                    var readSoFar = 0
                    while (readSoFar != 4)
                        readSoFar += inputStream.read(sizeBytes, readSoFar, 4 - readSoFar)
                    readSoFar = 0
                    val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
                    val buffer = ByteArray(size)
                    while (readSoFar != size)
                        readSoFar += inputStream.read(buffer, readSoFar, size - readSoFar)
                    val packet = Packet.getRootAsPacket(ByteBuffer.wrap(buffer))
                    Timber.d("Emitting packet of type ${packet.action}")
                    _packetFlow.emit(packet)
                } catch (e: Exception) {
                    Timber.e("Exception at startConnection: $e")
                    stopConnection()
                    break
                }
            }
        }
    }

    fun sendByteBuffer(buffer: ByteBuffer) {
        try {
            outputChannel.write(buffer)
        } catch (e: Exception) {
            Timber.e("Exception at sendByteBuffer: $e")
            scope.launch { stopConnection() }
        }
    }

    fun isConnected(): Boolean = clientSocket != null && clientSocket!!.isConnected

    private suspend fun stopConnection() {
        Timber.d("Stopping connection: $deviceId")
        _stateFlow.emit(LinkState(LinkState.State.DISCONNECTED, this))
        clientSocket?.close()
        scope.cancel()
    }

    fun observeState(): Flow<LinkState> = _stateFlow

}
package com.kuromelabs.kurome.network

import com.google.flatbuffers.FlatBufferBuilder
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kurome.Packet
import kurome.Result
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.cert.X509Certificate
import java.util.zip.GZIPOutputStream
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


@OptIn(ExperimentalUnsignedTypes::class)
class Link {
    private val selector: ActorSelectorManager = ActorSelectorManager(Dispatchers.IO)
    private val socketBuilder = aSocket(selector).tcp()
    var ip = String()
    private var clientSocket: Socket? = null
    private var out: ByteWriteChannel? = null
    private var `in`: ByteReadChannel? = null
    var builder = FlatBufferBuilder(1024)
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
        this.ip = ip

        clientSocket = socketBuilder.connect(InetSocketAddress(ip, port)).tls(
            Dispatchers.IO, tlsConfig
        )
        Timber.d("Link connected at $ip:$port")
        out = clientSocket!!.openWriteChannel(true)
        `in` = (clientSocket!!.openReadChannel())

    }

    suspend fun sendMessage(msg: ByteArray, gzip: Boolean): Byte {
        return try {
            out?.writeFully(addLittleEndianPrefix(if (gzip) byteArrayToGzip(msg) else msg))
            Packets.RESULT_ACTION_SUCCESS
        } catch (e: Exception) {
            stopConnection()
            Timber.d("link died at sendMessage")
            e.printStackTrace()
            Packets.RESULT_ACTION_FAIL
        }
    }

    suspend fun sendByteBuffer(packet: ByteBuffer) {
        try {
            out?.writeFully(packet)
        } catch (e: Exception) {
            stopConnection()
            Timber.d("link died at sendByteBuffer")
            e.printStackTrace()
        }
    }

    private val sizeBytes = ByteArray(4)
    private var size = 0
    private var buffer = ByteArray(1024)
    suspend fun receiveMessage(): ByteArray {
        return try {
            `in`?.readFully(sizeBytes)
            size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
            if (size > buffer.size) {
                buffer = ByteArray(size)
            }
            //Timber.e("size: $size")
            `in`?.readFully(buffer, 0, size)
            buffer
        } catch (e: Exception) {
            stopConnection()
            Timber.d("link died at receive")
            e.printStackTrace()
            byteArrayOf(Packets.RESULT_ACTION_FAIL)
        }
    }

    suspend fun receivePacket(packet: Packet): Byte {
        try {
            `in`?.readFully(sizeBytes)
            size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
            if (size > buffer.size) {
                buffer = ByteArray(size)
            }
            `in`?.readFully(buffer, 0, size)
        } catch (e: Exception) {
            stopConnection()
            Timber.d("link died at receivePacket")
            e.printStackTrace()
            return Result.resultActionFail
        }
        Packet.getRootAsPacket(ByteBuffer.wrap(buffer), packet)
        return Result.resultActionSuccess
    }

    fun stopConnection() {
        `in`?.cancel()
        out?.close()
        clientSocket?.close()
    }

    fun addLittleEndianPrefix(array: ByteArray): ByteArray {
        val size = Integer.reverseBytes(array.size)
        val sizeBytes = ByteBuffer.allocate(4).putInt(size).array()
        return sizeBytes + array
    }

    fun byteArrayToGzip(str: ByteArray): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream(str.size)
        val gzip = GZIPOutputStream(byteArrayOutputStream)
        gzip.write(str)
        gzip.close()
        val compressed = byteArrayOutputStream.toByteArray()
        byteArrayOutputStream.close()
        return compressed
    }
}
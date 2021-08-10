package com.kuromelabs.kurome.network

import android.util.Log
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPOutputStream


@Suppress("BlockingMethodInNonBlockingContext")
class TcpClient {
    private val selector: ActorSelectorManager = ActorSelectorManager(Dispatchers.IO)
    private val socketBuilder = aSocket(selector).tcp()
    private lateinit var clientSocket: Socket
    private var out: ByteWriteChannel? = null
    private var `in`: ByteReadChannel? = null

    suspend fun startConnection(ip: String?, port: Int) {

        clientSocket = socketBuilder.connect(InetSocketAddress(ip, port))
        Log.d("kurome", "connected to $ip:$port")
        out = clientSocket.openWriteChannel(true)
        `in` = (clientSocket.openReadChannel())

    }
    @Synchronized
    suspend fun sendMessage(msg: ByteArray, gzip: Boolean) {
        out?.writeFully(littleEndianPrefixedByteArray(if (msg.size > 100 && gzip) byteArrayToGzip(msg) else msg))
    }

    @Synchronized
    suspend fun receiveMessage(): ByteArray? {
        return try {
            val sizeBytes = ByteArray(4)
            `in`?.readFully(sizeBytes)
            val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
            var messageByte = ByteArray(0)
            while (messageByte.size != size) {
                val buffer = ByteArray(size)
                `in`?.readFully(buffer)
                messageByte += buffer
            }
            messageByte
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun stopConnection() {
        `in`?.cancel()
        out?.close()
        clientSocket.close()
    }

    fun littleEndianPrefixedByteArray(array: ByteArray): ByteArray {
        val size = Integer.reverseBytes(array.size)
        val sizeBytes = ByteBuffer.allocate(4).putInt(size).array()
        return sizeBytes + array
    }

    fun byteArrayToGzip(str: ByteArray): ByteArray {
        Log.d("com.kuromelabs.kurome", String(str))
        val byteArrayOutputStream = ByteArrayOutputStream(str.size)
        val gzip = GZIPOutputStream(byteArrayOutputStream)
        gzip.write(str)
        gzip.close()
        val compressed = byteArrayOutputStream.toByteArray()
        byteArrayOutputStream.close()
        return compressed
    }
}
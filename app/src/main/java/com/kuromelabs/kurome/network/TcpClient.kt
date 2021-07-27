package com.kuromelabs.kurome.network

import android.util.Log
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder


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

    suspend fun sendMessage(msg: ByteArray) {
        out?.writeFully(littleEndianPrefixedByteArray(msg))
    }
    @Synchronized
    suspend fun sendFile(path: String) {
        val fis = File(path).inputStream()
        var count: Int
        val buffer = ByteArray(4096)
        while (fis.read(buffer).also { count = it } > 0) {
            out?.writeFully(buffer, 0, count)
        }

    }

    suspend fun receiveMessage(): ByteArray {
        val sizeBytes = ByteArray(4)
        `in`?.readFully(sizeBytes)
        val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
        var messageByte = ByteArray(0)
        while (messageByte.size != size) {
            val buffer = ByteArray(size)
            `in`?.readFully(buffer)
            messageByte += buffer
        }
        return messageByte
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

}
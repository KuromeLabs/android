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
class Link {
    private val selector: ActorSelectorManager = ActorSelectorManager(Dispatchers.IO)
    private val socketBuilder = aSocket(selector).tcp()
    var ip = String()
    private var clientSocket: Socket? = null
    private var out: ByteWriteChannel? = null
    private var `in`: ByteReadChannel? = null

    suspend fun startConnection(ip: String, port: Int) {
        this.ip = ip
        clientSocket = socketBuilder.connect(InetSocketAddress(ip, port))
//        Log.d("kurome", "connected to $ip:$port")
        out = clientSocket!!.openWriteChannel(true)
        `in` = (clientSocket!!.openReadChannel())

    }

    suspend fun sendMessage(msg: ByteArray, gzip: Boolean): Byte {
        try {
            out?.writeFully(littleEndianPrefixedByteArray(
                    if (msg.size > 1500 && gzip) byteArrayToGzip(
                        msg
                    ) else msg
                )
            )
            return Packets.RESULT_ACTION_SUCCESS
        } catch (e: Exception){
            stopConnection()
            Log.e("kurome/link","link died at send")
            e.printStackTrace()
            return Packets.RESULT_ACTION_FAIL
        }
    }

    suspend fun receiveMessage(): ByteArray {
        return try {
            val sizeBytes = ByteArray(4)
            `in`?.readFully(sizeBytes)
            val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
            val buffer = ByteArray(size)
            `in`?.readFully(buffer)
            buffer
        } catch (e: Exception){
            stopConnection()
            Log.e("kurome/link","link died at receive")
            e.printStackTrace()
            byteArrayOf(Packets.RESULT_ACTION_FAIL)
        }
    }

    fun stopConnection() {
        `in`?.cancel()
        out?.close()
        clientSocket?.close()
    }

    fun littleEndianPrefixedByteArray(array: ByteArray): ByteArray {
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
package com.kuromelabs.kurome.network

import android.util.Log
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder


class SocketInstance {
    private var clientSocket: Socket? = null
    private var out: OutputStream? = null
    private var `in`: DataInputStream? = null

    fun startConnection(ip: String?, port: Int) {
        clientSocket = Socket(ip, port)
        Log.d("kurome","connected to $ip:$port")
        out = clientSocket!!.getOutputStream()
        `in` = DataInputStream(clientSocket!!.getInputStream())
    }

    fun sendMessage(msg: ByteArray) {
        out?.write(littleEndianPrefixedByteArray(msg))
        out?.flush()
    }
    fun sendFile(path: String){
        val fis = File(path).inputStream()
        var count: Int
        val buffer = ByteArray(4096)
        while (fis.read(buffer).also { count = it } > 0) {
            out!!.write(buffer, 0, count)
        }
    }
    fun receiveMessage(): ByteArray {
        val sizeBytes = ByteArray(4)
        `in`?.read(sizeBytes)
        val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
        var messageByte = ByteArray(0)
        while (messageByte.size != size){
            val buffer = ByteArray(size)
            `in`?.read(buffer)
            messageByte += buffer
        }
        return messageByte
    }

    fun stopConnection() {
        `in`?.close()
        out?.close()
        clientSocket?.close()
    }

    fun littleEndianPrefixedByteArray(array: ByteArray): ByteArray {
        val size = Integer.reverseBytes(array.size)
        val sizeBytes = ByteBuffer.allocate(4).putInt(size).array()
        return sizeBytes + array
    }

}
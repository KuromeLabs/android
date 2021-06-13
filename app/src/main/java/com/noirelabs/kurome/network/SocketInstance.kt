package com.noirelabs.kurome.network

import java.io.DataInputStream
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
        out = clientSocket!!.getOutputStream()
        `in` = DataInputStream(clientSocket!!.getInputStream())
    }

    fun sendMessage(msg: ByteArray) {
        out?.write(littleEndianPrefixedByteArray(msg))
        out?.flush()
    }

    fun receiveMessage(): String {
        val sizeBytes = ByteArray(4)
        `in`?.read(sizeBytes)
        val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
        val messageByte = ByteArray(size)
        val bytesRead = `in`?.read(messageByte)
        return String(messageByte, 0, bytesRead!!)
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
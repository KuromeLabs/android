package com.noirelabs.kurome.network

import android.util.Log
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.Socket


class SocketInstance {
    private var clientSocket: Socket? = null
    private var out: PrintWriter? = null
    private var `in`: DataInputStream? = null

    fun startConnection(ip: String?, port: Int) {
        clientSocket = Socket(ip, port)
        out = PrintWriter(clientSocket!!.getOutputStream(), true)
        `in` = DataInputStream(clientSocket!!.getInputStream())
    }

    fun sendMessage(msg: String?) {
        out?.println(msg)
        out?.flush()
    }

    fun receiveMessage(): String {
        val messageByte = ByteArray(1000)
        var dataString = ""

        try {
            val bytesRead = `in`?.read(messageByte)
            dataString += bytesRead?.let { String(messageByte, 0, it) }

            Log.d("Kurome","MESSAGE: $dataString")
        } catch (e: Exception) {
            e.printStackTrace()
            return e.toString()
        }
        return dataString
    }

    fun stopConnection() {
        `in`?.close()
        out?.close()
        clientSocket?.close()
    }

}
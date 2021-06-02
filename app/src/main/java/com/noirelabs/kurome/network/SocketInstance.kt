package com.noirelabs.kurome.network

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

            println("MESSAGE: $dataString")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return dataString
    }

    fun stopConnection() {
        `in`?.close()
        out?.close()
        clientSocket?.close()
    }


    fun receiveUDPMessage(ip: String, port: Int): String {
        val buffer = ByteArray(1024)
        val socket = MulticastSocket(port)
        val group = InetAddress.getByName(ip)
        socket.joinGroup(group)
        while (true) {
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            val msg = String(packet.data, packet.offset, packet.length)
            socket.close()
            return msg
        }
    }
}
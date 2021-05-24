package com.noirelabs.kurome.network

import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.Socket

class SocketInstance {
    private var clientSocket: Socket? = null
    private var out: PrintWriter? = null
    private var `in`: BufferedReader? = null

    fun startConnection(ip: String?, port: Int) {
        clientSocket = Socket(ip, port)
        out = PrintWriter(clientSocket!!.getOutputStream(), true)
        `in` = BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))
    }

    fun sendMessage(msg: String?) {
        out?.println(msg)
        out?.flush()
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
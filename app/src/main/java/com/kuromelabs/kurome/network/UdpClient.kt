package com.kuromelabs.kurome.network

import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

class UdpClient(port: Int) {
    private val socket = MulticastSocket(port)

    fun receiveUDPMessage(ip: String): String {

            val buffer = ByteArray(1024)
            val group = InetAddress.getByName(ip)
            socket.joinGroup(group)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            val msg = String(packet.data, packet.offset, packet.length)
            socket.close()
            return msg
    }

    fun close() {
        socket.close()
    }
}
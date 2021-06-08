package com.noirelabs.kurome.network

import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

class UdpClient {

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
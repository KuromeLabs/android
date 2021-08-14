package com.kuromelabs.kurome.network

import com.kuromelabs.kurome.Packets
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

@Suppress("BlockingMethodInNonBlockingContext")
class LinkProvider {
    suspend fun createControlLinkFromUdp(ip: String, port: Int): Link {
        val socket = MulticastSocket(port)
        val buffer = ByteArray(1024)
        val group = InetAddress.getByName(ip)
        socket.joinGroup(group)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        val msg = String(packet.data, packet.offset, packet.length)
        socket.close()
        val link = Link()
        link.startConnection(msg.split(':')[1], 33587)
        return link
    }

    suspend fun createLink(controlLink: Link): Link {
        val message = controlLink.receiveMessage()
        if (message[0] == Packets.ACTION_CREATE_NEW_LINK){
            val link = Link()
            link.startConnection(controlLink.ip, 33588)
            return link
        } else {
            throw InstantiationException()
        }
    }
}
package com.kuromelabs.kurome.network

import android.util.Log
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

@Suppress("BlockingMethodInNonBlockingContext")
object LinkProvider {
    suspend fun createControlLinkFromUdp(ip: String, port: Int): Link {
        Log.d("kurome/linkprovider","creating control link at $ip:$port")
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

    suspend fun createPairLink(ip: String, port: Int): Link{
        val link = Link()
        Log.d("kurome/linkprovider","starting link at $ip:$port")
        link.startConnection(ip, port)
        return link
    }
}
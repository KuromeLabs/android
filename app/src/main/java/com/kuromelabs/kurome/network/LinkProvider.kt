package com.kuromelabs.kurome.network

import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketException

@Suppress("BlockingMethodInNonBlockingContext")
object LinkProvider {
    suspend fun createControlLinkFromUdp(ip: String, port: Int, id: String): Link {
        Log.d("kurome/linkprovider","creating control link at $ip:$port, id: $id")
        var incomingId = String()
        var msg = String()
        val socket = MulticastSocket(port)
        val group = InetAddress.getByName(ip)
        socket.joinGroup(group)
        val buffer = ByteArray(1024)
        val packet = DatagramPacket(buffer, buffer.size)
        while (incomingId != id && currentCoroutineContext().job.isActive) {
            socket.receive(packet)
            msg = String(packet.data, packet.offset, packet.length)
            incomingId = msg.split(':')[3]
        }
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
            throw SocketException()
        }
    }

    suspend fun createPairLink(ip: String, port: Int): Link{
        val link = Link()
        Log.d("kurome/linkprovider","starting link at $ip:$port")
        link.startConnection(ip, port)
        return link
    }
}
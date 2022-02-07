package com.kuromelabs.kurome.network

import android.content.Context
import android.os.Build
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.getGuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kurome.Action
import kurome.DeviceInfo
import kurome.Packet
import timber.log.Timber
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("BlockingMethodInNonBlockingContext")
class LinkProvider(val context: Context) : Link.LinkDisconnectedCallback {
    private var udpSocket: MulticastSocket? = null
    var listening = false
    private val linkListeners = CopyOnWriteArrayList<LinkListener>()
    private val builder = FlatBufferBuilder(128)

    interface LinkListener {
        fun onLinkConnected(identityPacket: String?, link: Link?)
        fun onLinkDisconnected(id: String?, link: Link?)
    }


    fun initializeUdpListener(ip: String, port: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            Timber.d("initializing udp listener at $ip:$port")
            udpSocket = MulticastSocket(33586)
            val group = InetAddress.getByName(ip)
            udpSocket!!.joinGroup(group)
            Timber.e("Listening: $listening")
            while (listening) {

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    Timber.e("attempting to receive UDP packet")
                    udpSocket!!.receive(packet)
                    Timber.d("received packet: ${String(packet.data, packet.offset, packet.length)}")
                    datagramPacketReceived(packet)
                } catch (e: SocketException) {
                    Timber.e(e)
                }
            }
        }
    }

    private suspend fun datagramPacketReceived(packet: DatagramPacket) {
            val packetString = String(packet.data, packet.offset, packet.length)
            val split = packetString.split(':')
            val ip = split[1]
            val name = split[2]
            val id = split[3]
            Timber.d("Received UDP. name: $name, id: $id")
            val link = Link(id, this@LinkProvider)
            link.startConnection(ip, 33587)
            linkConnected(packetString, link)
            Timber.d("Link connection started from UDP")
            val modelOffset = builder.createString(Build.MODEL)
            val guidOffset = builder.createString(getGuid(context))

            val info = DeviceInfo.createDeviceInfo(builder, modelOffset, guidOffset, 0, 0, 0)
            val packet = Packet.createPacket(builder, 0, Action.actionConnect, 0, info, 0, 0)
            builder.finishSizePrefixed(packet)
        try {
            link.sendByteBuffer(builder.dataBuffer())
        } catch (e: Exception) {
            Timber.e("died at datagramPacketReceived: $e")
        }
            Timber.d("Sent identity packet. Size: ${builder.dataBuffer().capacity()}")
            builder.clear()


    }


    fun onStop() {
        listening = false;
        udpSocket?.close()
    }


    fun addLinkListener(linkListener: LinkListener) {
        linkListeners.add(linkListener)
    }

    fun removeLinkListener(linkListener: LinkListener) {
        linkListeners.remove(linkListener)
    }

    private fun linkConnected(packet: String, link: Link) {
        for (receiver in linkListeners) {
            Timber.d("linkConnected called. Num of callbacks: ${linkListeners.size}")
            receiver.onLinkConnected(packet, link)
        }
    }

    private fun linkDisconnected(link: Link) {
        for (receiver in linkListeners) {
            Timber.d("disconnected link: ${link.deviceId}")
            receiver.onLinkDisconnected(link.deviceId, link)
        }
    }

    override fun onLinkDisconnected(link: Link) {
        linkDisconnected(link)
    }
}
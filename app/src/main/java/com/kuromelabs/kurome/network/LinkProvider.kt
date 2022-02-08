package com.kuromelabs.kurome.network

import android.content.Context
import android.os.Build
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.getGuid
import kotlinx.coroutines.*
import kurome.Action
import kurome.DeviceInfo
import kurome.Packet
import timber.log.Timber
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("BlockingMethodInNonBlockingContext")
class LinkProvider(private val context: Context, private val serviceScope: CoroutineScope) :
    Link.LinkDisconnectedCallback {
    private var udpSocket: MulticastSocket? = null
    var listening = false
    private val linkListeners = CopyOnWriteArrayList<LinkListener>()
    private val builder = FlatBufferBuilder(128)
    val activeLinks = ConcurrentHashMap<String, Link>()

    interface LinkListener {
        fun onLinkConnected(packetString: String?, link: Link?)
        fun onLinkDisconnected(id: String?, link: Link?)
    }


    fun initializeUdpListener(ip: String, port: Int) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                Timber.d("initializing udp listener at $ip:$port")
                udpSocket = MulticastSocket(33586)
                udpSocket!!.soTimeout = 5000
                val group = InetAddress.getByName(ip)
                udpSocket!!.joinGroup(group)
                Timber.e("Listening: $listening")
                while (listening) {
                    val buffer = ByteArray(1024)
                    val packet = DatagramPacket(buffer, buffer.size)
                    Timber.e("attempting to receive UDP packet")
                    udpSocket!!.receive(packet)
                    Timber.d("received UDP: ${String(packet.data, packet.offset, packet.length)}")
                    launch { datagramPacketReceived(packet) }
                }
            } catch (e: SocketException) {
                Timber.e("Exception while receiving UDP packet: $e")
            } catch (e: SocketTimeoutException) {
                Timber.d("Socket timeout while receiving UDP packet")
            }
        }

    }

    private fun datagramPacketReceived(packet: DatagramPacket) {
        try {
            val packetString = String(packet.data, packet.offset, packet.length)
            val split = packetString.split(':')
            val ip = split[1]
            val name = split[2]
            val id = split[3]
            Timber.d("Received UDP. name: $name, id: $id")
            val link = Link(id, this)
            link.startConnection(ip, 33587)
            linkConnected(packetString, link)
            Timber.d("Link connection started from UDP")
            val modelOffset = builder.createString(Build.MODEL)
            val guidOffset = builder.createString(getGuid(context))

            val info = DeviceInfo.createDeviceInfo(builder, modelOffset, guidOffset, 0, 0, 0)
            val result = Packet.createPacket(builder, 0, Action.actionConnect, 0, info, 0, 0)
            builder.finishSizePrefixed(result)
            link.sendByteBuffer(builder.dataBuffer())
            Timber.d("Sent identity packet. Size: ${builder.dataBuffer().capacity()}")
            builder.clear()
        } catch (e: Exception) {
            Timber.e("Exception at datagramPacketReceived: $e")
        }

    }


    fun onStop() {
        listening = false
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
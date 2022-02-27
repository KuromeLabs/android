package com.kuromelabs.kurome.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.content.ContextCompat
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
    private val udpIp = "235.132.20.12"
    private val udpPort = 33586

    interface LinkListener {
        fun onLinkConnected(packetString: String?, link: Link?)
        fun onLinkDisconnected(id: String?, link: Link?)
    }

    fun initialize() {
        setUdpSocket()
        initializeNetworkCallback()
        serviceScope.launch(Dispatchers.IO) {
            Timber.d("initializing udp listener at $udpIp:$udpPort")
            while (listening) {
                if (udpSocket != null && !udpSocket!!.isClosed)
                    try {
                        val buffer = ByteArray(1024)
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket!!.receive(packet)
                        Timber.d("received UDP: ${String(packet.data, packet.offset, packet.length)}")
                        launch { datagramPacketReceived(packet) }
                    } catch (e: Exception) {
                        setUdpSocket()
                        Timber.d("Exception at initializeUdpListener: $e")
                    }
            }
        }
    }

    private fun initializeNetworkCallback() {
        val cm = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(net: Network, capabilities: NetworkCapabilities) {
                Timber.d("Monitor network capabilities: $capabilities network: $net")
                udpSocket?.close()
            }
            override fun onLost(net: Network) {
                Timber.d("Monitor network lost: $net")
                udpSocket?.close()
                for (link in activeLinks.values) {
                    link.stopConnection()
                }
            }
        })
    }

    fun setUdpSocket() {
        try {
            Timber.d("Setting up socket")
            udpSocket?.close()
            udpSocket = MulticastSocket(33586)
            udpSocket?.reuseAddress = false
            udpSocket?.broadcast = true
            val group = InetAddress.getByName(udpIp)
            udpSocket?.joinGroup(group)
        } catch (e: Exception) {
            Timber.d("Exception at setUdpSocket: $e")
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
            if (activeLinks.containsKey(id)) {
                Timber.d("Link already exists, not connecting")
                return
            }
            val link = Link(id, this)
            link.startConnection(ip, 33587)
            activeLinks[id] = link
            linkConnected(packetString, link)
            Timber.d("Link connection started from UDP")
            val modelOffset = builder.createString(Build.MODEL)
            val guidOffset = builder.createString(getGuid(context))

            val info = DeviceInfo.createDeviceInfo(builder, modelOffset, guidOffset, 0, 0, 0)
            val result = Packet.createPacket(builder, 0, Action.actionConnect, 0, info, 0, 0, 0)
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
        activeLinks.remove(link.deviceId)
        for (receiver in linkListeners) {
            Timber.d("disconnected link: ${link.deviceId}")
            receiver.onLinkDisconnected(link.deviceId, link)
        }
    }

    override fun onLinkDisconnected(link: Link) {
        linkDisconnected(link)
    }
}
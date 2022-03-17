package com.kuromelabs.kurome.domain.util.link

import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import com.google.flatbuffers.FlatBufferBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kurome.Action
import kurome.DeviceInfo
import kurome.Packet
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Suppress("BlockingMethodInNonBlockingContext")
class LinkProvider(val context: Context) {
    private var udpSocket: DatagramSocket? = null
    private val builder = FlatBufferBuilder(128)
    private val activeLinks = ConcurrentHashMap<String, Link>()
    private val udpIp = "255.255.255.255"
    private val udpPort = 33586
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var listenJob: Job? = null

    private val _linkFlow = MutableSharedFlow<LinkState>(0)

    init {
        setUdpListener()
        startUdpListener()
    }

    private fun setUdpListener() {
        try {
            Timber.d("Setting up socket")
            udpSocket?.close()
            udpSocket = DatagramSocket(null)
            udpSocket?.reuseAddress = true
            udpSocket?.broadcast = true
            udpSocket?.bind(InetSocketAddress(udpPort))
        } catch (e: Exception) {
            Timber.d("Exception at setUdpListener: $e")
        }
    }

    private fun startUdpListener() {
        listenJob?.cancel()
        listenJob = scope.launch(Dispatchers.IO) {
            Timber.d("initializing udp listener at $udpIp:$udpPort")
            while (currentCoroutineContext().isActive) {
                if (udpSocket != null && !udpSocket!!.isClosed)
                    try {
                        val buffer = ByteArray(1024)
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket!!.receive(packet)
                        Timber.d("received UDP: ${String(packet.data, packet.offset, packet.length)}")
                        launch { datagramPacketReceived(packet) }
                    } catch (e: Exception) {
                        Timber.d("Exception at initializeUdpListener: $e")
                    }
            }
        }
    }

    @ExperimentalUnsignedTypes
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
            val link = Link(id, name, ip, 33587)
            observeLinkState(link)
            Timber.d("Link connection started from UDP")
            val modelOffset = builder.createString(Build.MODEL)
            val guidOffset = builder.createString(getGuid(context))

            val info = DeviceInfo.createDeviceInfo(builder, modelOffset, guidOffset, 0, 0, 0)
            val result = Packet.createPacket(builder, 0, Action.actionConnect, 0, info, 0, 0, 0, 0)
            builder.finishSizePrefixed(result)
            link.sendByteBuffer(builder.dataBuffer())
            Timber.d("Sent identity packet. Size: ${builder.dataBuffer().capacity()}")
            builder.clear()
        } catch (e: Exception) {
            Timber.e("Exception at datagramPacketReceived: $e")
        }

    }

    private fun observeLinkState(link: Link) {
        var linkJob: Job? = null
        linkJob = scope.launch {
            link.observeState().collect {
                when (it.state) {
                    LinkState.State.CONNECTED -> {
                        _linkFlow.emit(LinkState(LinkState.State.CONNECTED, link))
                        activeLinks[link.deviceId] = link
                    }
                    LinkState.State.DISCONNECTED -> {
                        _linkFlow.emit(LinkState(LinkState.State.DISCONNECTED, link))
                        activeLinks.remove(link.deviceId)
                        linkJob?.cancel()
                        if (!linkJob!!.isActive) Timber.e("linkJob was cancelled in linkDisconnected")
                    }
                }
            }
        }
    }

    fun observeLinks(): Flow<LinkState> = _linkFlow

    fun onStop() {
        listenJob?.cancel()
        udpSocket?.close()
    }

    private fun getGuid(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        var id = preferences.getString("id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            preferences.edit().putString("id", id).apply()
        }
        return id
    }
}
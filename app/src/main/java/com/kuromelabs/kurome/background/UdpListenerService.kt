package com.kuromelabs.kurome.background

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import javax.inject.Inject

class UdpListenerService @Inject constructor(
    var scope: CoroutineScope,
    var handler: DeviceConnectionHandler
) {
    private val udpListenPort = 33586
    fun start() {
        scope.launch {
            try {
                Timber.d("Setting up socket")
                val udpSocket = DatagramSocket(null)
                udpSocket.reuseAddress = true
                udpSocket.broadcast = true
                udpSocket.bind(InetSocketAddress(udpListenPort))


                while (currentCoroutineContext().isActive && !udpSocket.isClosed) {
                    if (!udpSocket.isClosed) {
                        val buffer = ByteArray(1024)
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket.receive(packet)
                        val message = String(packet.data, packet.offset, packet.length)
                        Timber.d("received UDP: $message")
                        launch {
                            handler.handleClientConnection(
                                message.split(':')[2],
                                message.split(':')[3],
                                message.split(':')[1],
                                33587
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.d("Exception at UdpListenerService: $e")
            }
        }
    }
}
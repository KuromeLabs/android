package com.kuromelabs.kurome.infrastructure.network


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kurome.fbs.Packet
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import javax.net.ssl.SSLSocket


class Link(var socket: SSLSocket, var scope: CoroutineScope) {
    interface PacketReceiver {
        fun processPacket(packet: Packet)
    }

    private val outputChannel = Channels.newChannel(socket.outputStream)
    private var device: PacketReceiver? = null
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> get() = _isConnected
    suspend fun receive(buffer: ByteArray, size: Int): Int {
        return try {
            var bytesRead = 0
            while (bytesRead != size && bytesRead >= 0)
                bytesRead += socket.inputStream.read(buffer, bytesRead, size - bytesRead)
            bytesRead
        } catch (e: Exception) {
            Timber.e(e, "Error receiving data")
            0
        }
    }

    fun setDevice(device: PacketReceiver) {
        this.device = device
    }

    fun send(buffer: ByteBuffer) {
        synchronized(this) {
            try {
                outputChannel.write(buffer)
            } catch (e: Exception) {
                close()
                Timber.e(e, "Error sending data")
            }
        }
    }

    private fun close() {
        _isConnected.value = false
        outputChannel.close()
        socket.close()
    }

    fun start() {
        _isConnected.value = true
        scope.launch {
            while (scope.coroutineContext.isActive) {
                val sizeBuffer = ByteArray(4)
                if (receive(sizeBuffer, 4) <= 0) break
                val size = ByteBuffer.wrap(sizeBuffer).order(ByteOrder.LITTLE_ENDIAN).int
                val data = ByteArray(size)
                if (receive(data, size) <= 0) break
                val packet = flatBufferHelper.deserializePacket(data)
                device!!.processPacket(packet)
            }
            Timber.d("Closing socket")
            close()
        }
    }
}
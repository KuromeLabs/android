package com.kuromelabs.kurome.infrastructure.network


import Kurome.Fbs.Packet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import javax.net.ssl.SSLSocket


class Link(var socket: SSLSocket, var scope: CoroutineScope) {


    private val outputChannel = Channels.newChannel(socket.outputStream)
    private val _receivedPackets = MutableSharedFlow<Packet>()
    val receivedPackets = _receivedPackets.asSharedFlow()
    private val _isConnected = MutableSharedFlow<Boolean>(1)

    val isConnected = _isConnected.asSharedFlow()
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

    fun close() {
        _isConnected.tryEmit(false)
        outputChannel.close()
        socket.close()
    }

    suspend fun start() {
        _isConnected.emit(true)
        while (scope.coroutineContext.isActive) {
            val sizeBuffer = ByteArray(4)
            if (receive(sizeBuffer, 4) <= 0) break
            val size = ByteBuffer.wrap(sizeBuffer).order(ByteOrder.LITTLE_ENDIAN).int
            val data = ByteArray(size)
            if (receive(data, size) <= 0) break
            val packet = Packet.getRootAsPacket(ByteBuffer.wrap(data))
            _receivedPackets.emit(packet)
        }
        Timber.d("Closing socket")
        close()

    }
}
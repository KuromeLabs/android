package com.kuromelabs.kurome.infrastructure.network


import Kurome.Fbs.Packet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import javax.net.ssl.SSLSocket


class Link(private var socket: SSLSocket, private var scope: CoroutineScope) {


    private val outputChannel = Channels.newChannel(socket.outputStream)
    private val _receivedPackets = MutableSharedFlow<Packet>()
    val receivedPackets = _receivedPackets.asSharedFlow()
    private val _isConnected = MutableSharedFlow<Boolean>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val isConnected = _isConnected.asSharedFlow()

    init {
        _isConnected.tryEmit(true)
    }

    private fun receive(buffer: ByteArray, size: Int): Int {
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
                _isConnected.tryEmit(false)
                Timber.e(e, "Error sending data")
            }
        }
    }

    fun close() {
        if (socket.isClosed) return
        Timber.d("Closing link")
        try {
            outputChannel.close()
            socket.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing socket")
        }
    }

    fun start() = scope.launch {
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
        _isConnected.tryEmit(false)

    }
}
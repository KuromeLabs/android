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

class LinkBrokenException: Exception()

class LinkPacket(val value: Packet?, private val error: Exception?) {
    companion object {
        fun success(value: Packet): LinkPacket {
            return LinkPacket(value, null)
        }

        fun failure(error: Exception): LinkPacket {
            return LinkPacket(null, error)
        }
    }

    val isSuccess: Boolean
        get() = value != null

    val isFailure: Boolean
        get() = error != null

    suspend fun onSuccess(action: suspend (Packet) -> Unit): LinkPacket {
        value?.let { action(it) }
        return this
    }

    suspend fun onFailure(action: suspend (Exception) -> Unit): LinkPacket {
        error?.let { action(it) }
        return this
    }
}


class Link(private var socket: SSLSocket, private var scope: CoroutineScope) {

    private val outputChannel = Channels.newChannel(socket.outputStream)
    private val _receivedPackets = MutableSharedFlow<LinkPacket>(extraBufferCapacity = 1000, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val receivedPackets = _receivedPackets.asSharedFlow()

    private fun receive(buffer: ByteArray, size: Int): Int {
        return try {
            var totalBytesRead = 0
            while (totalBytesRead != size) {
                val bytesRead = socket.inputStream.read(
                    buffer,
                    totalBytesRead,
                    size - totalBytesRead
                )
                if (bytesRead == -1)
                    return 0
                totalBytesRead += bytesRead
            }
            totalBytesRead
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
                _receivedPackets.tryEmit(LinkPacket.failure(e))
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
            println("Socket closed")
        } catch (e: Exception) {
            Timber.e(e, "Error closing socket")
        }
    }

    fun start() = scope.launch {
        while (scope.coroutineContext.isActive) {
            val sizeBuffer = ByteArray(4)
            if (receive(sizeBuffer, 4) <= 0) break
            val size = ByteBuffer.wrap(sizeBuffer).order(ByteOrder.LITTLE_ENDIAN).int
            val data = ByteArray(size)
            if (receive(data, size) <= 0) break
            try {
                val packet = Packet.getRootAsPacket(ByteBuffer.wrap(data))
                _receivedPackets.emit(LinkPacket.success(packet))
            } catch (e: Exception) {
                Timber.e(e, "Received malformed packet")
                break
            }
        }
        Timber.d("Emitting link broken result")
        _receivedPackets.emit(LinkPacket.failure(LinkBrokenException()))
    }
}
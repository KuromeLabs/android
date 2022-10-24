package com.kuromelabs.kurome.infrastructure.network

import com.kuromelabs.kurome.application.interfaces.Link
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.channels.Channels
import javax.net.ssl.SSLSocket

class LinkImpl(var socket: SSLSocket) : Link {
    private val outputChannel = Channels.newChannel(socket.outputStream)
    override suspend fun receive(buffer: ByteArray, size: Int): Int {
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

    override suspend fun send(buffer: ByteBuffer) {
        synchronized(this) { outputChannel.write(buffer) }
    }

    override suspend fun close() {
        outputChannel.close()
        socket.close()
    }
}
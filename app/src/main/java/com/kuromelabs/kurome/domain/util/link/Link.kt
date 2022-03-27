package com.kuromelabs.kurome.domain.util.link

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kurome.Packet
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel
import javax.net.ssl.SSLSocket


@OptIn(ExperimentalUnsignedTypes::class)
class Link(val deviceId: String, val deviceName: String, private val socket: SSLSocket) {
    private var inputStream: InputStream = socket.inputStream
    private var outputStream: OutputStream = socket.outputStream
    private var outputChannel: WritableByteChannel = Channels.newChannel(outputStream)


    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val _packetFlow = MutableSharedFlow<Packet>(0)
    val packetFlow: SharedFlow<Packet> = _packetFlow

    private val _stateFlow = MutableSharedFlow<LinkState>(1)

    init {
        startListening()
        scope.launch { _stateFlow.emit(LinkState(LinkState.State.CONNECTED, this@Link)) }
    }


    private fun startListening() {
        scope.launch {
            while (true) {
                try {
                    val sizeBytes = ByteArray(4)
                    var readSoFar = 0
                    while (readSoFar != 4)
                        readSoFar += inputStream.read(sizeBytes, readSoFar, 4 - readSoFar)
                    readSoFar = 0
                    val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
                    val buffer = ByteArray(size)
                    while (readSoFar != size)
                        readSoFar += inputStream.read(buffer, readSoFar, size - readSoFar)
                    val packet = Packet.getRootAsPacket(ByteBuffer.wrap(buffer))
                    Timber.d("Emitting packet of type ${packet.action}")
                    _packetFlow.emit(packet)
                } catch (e: Exception) {
                    Timber.e("Exception at startConnection: $e")
                    stopConnection()
                    break
                }
            }
        }
    }

    fun sendByteBuffer(buffer: ByteBuffer) {
        try {
            outputChannel.write(buffer)
        } catch (e: Exception) {
            Timber.e("Exception at sendByteBuffer: $e")
            scope.launch { stopConnection() }
        }
    }

    fun isConnected(): Boolean = socket.isConnected

    private suspend fun stopConnection() {
        Timber.d("Stopping connection: $deviceId")
        _stateFlow.emit(LinkState(LinkState.State.DISCONNECTED, this))
        socket.close()
        scope.cancel()
    }

    fun observeState(): Flow<LinkState> = _stateFlow

}
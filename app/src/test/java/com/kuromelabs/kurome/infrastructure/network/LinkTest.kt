package com.kuromelabs.kurome.infrastructure.network

import Kurome.Fbs.Component
import Kurome.Fbs.DeviceIdentityResponse
import Kurome.Fbs.Packet
import Kurome.Fbs.Platform
import com.google.flatbuffers.FlatBufferBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer
import javax.net.ssl.SSLSocket
class LinkTest {

    lateinit var scope: CoroutineScope
    lateinit var dispatcherScope: CoroutineScope
    lateinit var mockSSLSocket: SSLSocket

    private fun samplePacketByteArray(): ByteArray {
        val builder = FlatBufferBuilder(256)
        val p = Packet.createPacket(
            builder,
            Component.DeviceIdentityResponse,
            DeviceIdentityResponse.createDeviceIdentityResponse(builder, 0, 0, builder.createString(""), builder.createString(""), builder.createString(""), Platform.Android),
            1
        )
        builder.finishSizePrefixed(p)
        return builder.sizedByteArray()
    }

    private fun createMockSocket(inputStream: PipedInputStream, outputStream: PipedOutputStream): SSLSocket {
        return mock {
            on { getInputStream() } doReturn inputStream
            on { getOutputStream() } doReturn outputStream
        }
    }

    @BeforeEach
    fun setup() {
        scope = TestScope()
        dispatcherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val pipeInput = PipedInputStream()
        val pipedOutputStream = PipedOutputStream()
        mockSSLSocket = createMockSocket(pipeInput, pipedOutputStream)
    }

    @AfterEach
    fun tearDown() {
        dispatcherScope.cancel()
    }

    @Test
    fun `test link send with a simple buffer and read the buffer back`() {
        val mockSslSocketSentDataStream = PipedInputStream()
        whenever(mockSSLSocket.outputStream).doReturn(PipedOutputStream(mockSslSocketSentDataStream))
        val link = Link(mockSSLSocket, scope)
        val inputBuffer = ByteBuffer.wrap(byteArrayOf(1,2,3,4,5,6,7,8,9,10))

        link.send(inputBuffer)

        val buffer = ByteBuffer.allocate(10)
        var bytesRead = 0
        while (bytesRead != 10 && bytesRead >= 0)
            bytesRead += mockSslSocketSentDataStream.read(buffer.array(), bytesRead, 10 - bytesRead)

        assert(inputBuffer.array().contentEquals(buffer.array()))
    }

    @Test
    fun `test link fbs packet receive loop with a normal packet emits success`() = runTest {
        val link = Link(mockSSLSocket, dispatcherScope)
        val mockSslSocketReceivedDataStream = PipedOutputStream()
        whenever(mockSSLSocket.inputStream).thenReturn(PipedInputStream(mockSslSocketReceivedDataStream))
        mockSslSocketReceivedDataStream.write(samplePacketByteArray())
        var result: Result<Packet>? = null
        val job = dispatcherScope.launch(Dispatchers.Unconfined) {
            result = link.receivedPackets.first()
        }
        link.start()
        job.join()
        assert(result!!.isSuccess && result!!.getOrNull()!!.componentType == Component.DeviceIdentityResponse)
    }

    @Test
    fun `test link fbs packet receive loop with many packets emits success`() = runTest {
        val mockSslSocketReceivedDataStream = PipedOutputStream()
        whenever(mockSSLSocket.inputStream).thenReturn(PipedInputStream(mockSslSocketReceivedDataStream))
        val link = Link(mockSSLSocket, dispatcherScope)
        val list = mutableListOf<Result<Packet>>()
        dispatcherScope.launch {
            for (i in 0..10000)
                mockSslSocketReceivedDataStream.write(samplePacketByteArray())
        }
        val job = dispatcherScope.launch(Dispatchers.Unconfined) {
            link.receivedPackets
                .takeWhile { list.size < 10000 }
                .collect {
                    list.add(it)
                }
        }

        link.start()
        job.join()
        assert(list.size == 10000)
    }


    @Test
    fun `test link fbs packet receive loop with malformed buffer emits failure`() = runTest {
        val mockSslSocketReceivedDataStream = PipedOutputStream()
        whenever(mockSSLSocket.inputStream).thenReturn(PipedInputStream(mockSslSocketReceivedDataStream))
        val link = Link(mockSSLSocket, dispatcherScope)
        val builder = FlatBufferBuilder(256)
        val p = Packet.createPacket(
            builder,
            Component.DeviceIdentityResponse,
            DeviceIdentityResponse.createDeviceIdentityResponse(builder, 0, 0, builder.createString(""), builder.createString(""), builder.createString(""), Platform.Android),
            1
        )
        builder.finishSizePrefixed(p)
        val bytes = builder.sizedByteArray()
        for (i in 5..<bytes.size)
            bytes[i] = 2
        var result: Result<Packet>? = null
        val job = dispatcherScope.launch(Dispatchers.Unconfined) {
            result = link.receivedPackets.first()
        }
        link.start()
        mockSslSocketReceivedDataStream.write(bytes)

        job.join()
        assert(result!!.isFailure)
    }

    @Test
    fun `test link fbs packet receive loop with error in stream after reading size of buffer emits failure`() = runTest {
        val mockSslSocketReceivedDataStream = PipedOutputStream()
        whenever(mockSSLSocket.inputStream).thenReturn(PipedInputStream(mockSslSocketReceivedDataStream))
        val link = Link(mockSSLSocket, dispatcherScope)
        val bytes = samplePacketByteArray()
        var result: Result<Packet>? = null
        val job = dispatcherScope.launch(Dispatchers.Unconfined) {
            result = link.receivedPackets.first()
        }
        link.start()
        mockSslSocketReceivedDataStream.write(bytes.copyOfRange(0, 4))
        whenever(mockSSLSocket.inputStream).thenThrow(IOException())
        mockSslSocketReceivedDataStream.write(bytes.copyOfRange(4, bytes.size))

        job.join()
        assert(result!!.isFailure)
    }

    @Test
    fun `test link fbs packet receive loop with scope cancelled emits failure`() = runTest {
        val mockSslSocketReceivedDataStream = PipedOutputStream()
        whenever(mockSSLSocket.inputStream).thenReturn(PipedInputStream(mockSslSocketReceivedDataStream))
        val linkScope = CoroutineScope(Dispatchers.IO)
        val link = Link(mockSSLSocket, linkScope)

        val job = dispatcherScope.launch(Dispatchers.Unconfined) {
            link.receivedPackets.first { it.isFailure }
        }

        link.start()
        mockSslSocketReceivedDataStream.write(samplePacketByteArray())
        linkScope.cancel()
        mockSslSocketReceivedDataStream.write(samplePacketByteArray())
        job.join()
    }


    @Test
    fun `test link fbs packet receive loop with massive packet`() = runTest {
        val mockSslSocketReceivedDataStream = PipedOutputStream()
        whenever(mockSSLSocket.inputStream).thenReturn(PipedInputStream(mockSslSocketReceivedDataStream))
        val link = Link(mockSSLSocket, dispatcherScope)
        val builder = FlatBufferBuilder(256)
        val p = Packet.createPacket(
            builder,
            Component.DeviceIdentityResponse,
            DeviceIdentityResponse.createDeviceIdentityResponse(builder, 0, 0, builder.createString("A".repeat(100000)), builder.createString(""), builder.createString(""), Platform.Android),
            1
        )
        builder.finishSizePrefixed(p)
        val bytes = builder.sizedByteArray()

        var result: Result<Packet>? = null
        val job = dispatcherScope.launch(Dispatchers.Unconfined) {
            result = link.receivedPackets.first()
        }
        link.start()
        mockSslSocketReceivedDataStream.write(bytes)

        job.join()
        assert(result!!.isSuccess && result!!.getOrNull()!!.componentType == Component.DeviceIdentityResponse)
    }

    @Test
    fun `test close emits failure result in start loop and closes the socket`() = runTest {
        val link = Link(mockSSLSocket, dispatcherScope)

        var result: Result<Packet>? = null
        val job = dispatcherScope.launch(Dispatchers.Unconfined) {
            result = link.receivedPackets.first()
            return@launch
        }
        link.start()
        link.close()
        job.join()
        assert(result!!.isFailure)
    }


    @Test
    fun `test send function while socket is closed, emit failure result in send function`() = runTest {
        val link = Link(mockSSLSocket, dispatcherScope)

        var result: Result<Packet>? = null
        val job = dispatcherScope.launch(Dispatchers.Unconfined) {
            result = link.receivedPackets.first()
            return@launch
        }
        link.close()
        link.send(ByteBuffer.allocate(1))
        job.join()
        assert(result!!.isFailure)
    }

    @Test
    fun `test close function twice does not throw exception`() = runTest {
        val link = Link(mockSSLSocket, dispatcherScope)
        assertDoesNotThrow {
            link.close()
            link.close()
        }
    }

    @Test
    fun `test close function with IOError does not throw exception`() = runTest {
        whenever(mockSSLSocket.close()).thenThrow(IOException())
        val link = Link(mockSSLSocket, dispatcherScope)
        assertDoesNotThrow {
            link.close()
        }
    }

    @Test
    fun `test close function with socket already closed does not throw exception`() = runTest {
        whenever(mockSSLSocket.isClosed).thenReturn(true)
        val link = Link(mockSSLSocket, dispatcherScope)
        assertDoesNotThrow {
            link.close()
        }
    }

}
package com.kuromelabs.kurome.infrastructure.network

import Kurome.Fbs.Component
import Kurome.Fbs.DeviceIdentityResponse
import Kurome.Fbs.Packet
import Kurome.Fbs.Platform
import com.google.flatbuffers.FlatBufferBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
            DeviceIdentityResponse.createDeviceIdentityResponse(builder, 0, 0, builder.createString(""), builder.createString(""), builder.createString(""), Platform.Android, 33587u),
            1
        )
        builder.finishSizePrefixed(p)
        return builder.sizedByteArray()
    }

    private fun createMockSocket(inputStream: PipedInputStream, outputStream: PipedOutputStream): SSLSocket {
        return mockk<SSLSocket>(relaxed = true) {
            every { getInputStream() } answers { inputStream }
            every { getOutputStream() } answers { outputStream }
            every { isClosed } returns false
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
        every {mockSSLSocket.outputStream} answers {PipedOutputStream(mockSslSocketSentDataStream)}
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
        val input = PipedInputStream(mockSslSocketReceivedDataStream)
        every { mockSSLSocket.inputStream } answers {input}
        mockSslSocketReceivedDataStream.write(samplePacketByteArray())
        var result: LinkPacket? = null
        val job = dispatcherScope.launch(Dispatchers.Unconfined) {
            result = link.receivedPackets.first()
        }
        link.start()
        job.join()
        assert(result!!.isSuccess && result!!.value!!.componentType == Component.DeviceIdentityResponse)
    }

    @Test
    fun `test link fbs packet receive loop with many packets emits success`() = runTest {
        val mockSslSocketReceivedDataStream = PipedOutputStream()
        val input = PipedInputStream(mockSslSocketReceivedDataStream)
        every{mockSSLSocket.inputStream} answers {input}
        val link = Link(mockSSLSocket, dispatcherScope)
        val list = mutableListOf<LinkPacket>()
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
        val input = PipedInputStream(mockSslSocketReceivedDataStream)
        every {mockSSLSocket.inputStream} answers {input}
        val link = Link(mockSSLSocket, dispatcherScope)
        val builder = FlatBufferBuilder(256)
        val p = Packet.createPacket(
            builder,
            Component.DeviceIdentityResponse,
            DeviceIdentityResponse.createDeviceIdentityResponse(builder, 0, 0, builder.createString(""), builder.createString(""), builder.createString(""), Platform.Android, 0u),
            1
        )
        builder.finishSizePrefixed(p)
        val bytes = builder.sizedByteArray()
        for (i in 5..<bytes.size)
            bytes[i] = 2
        var result: LinkPacket? = null
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
        val input = PipedInputStream(mockSslSocketReceivedDataStream)
        every { mockSSLSocket.inputStream } answers {
            input
        } andThenThrows IOException()
        val link = Link(mockSSLSocket, dispatcherScope)
        val bytes = samplePacketByteArray()
        var result: LinkPacket? = null
        val job = dispatcherScope.launch(Dispatchers.Unconfined) {
            result = link.receivedPackets.first()
        }

        mockSslSocketReceivedDataStream.write(bytes.copyOfRange(0, 4))
        mockSslSocketReceivedDataStream.write(bytes.copyOfRange(4, bytes.size))
        link.start()
        job.join()
        assert(result!!.isFailure)
    }

    @Test
    fun `test link fbs packet receive loop with scope cancelled emits failure`() = runTest {
        val mockSslSocketReceivedDataStream = PipedOutputStream()
        val input = PipedInputStream(mockSslSocketReceivedDataStream)
        every {mockSSLSocket.inputStream} answers {input}
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
        val input = PipedInputStream(mockSslSocketReceivedDataStream)
        every{mockSSLSocket.inputStream} answers {input}
        val link = Link(mockSSLSocket, dispatcherScope)
        val builder = FlatBufferBuilder(256)
        val p = Packet.createPacket(
            builder,
            Component.DeviceIdentityResponse,
            DeviceIdentityResponse.createDeviceIdentityResponse(builder, 0, 0, builder.createString("A".repeat(100000)), builder.createString(""), builder.createString(""), Platform.Android, 0u),
            1
        )
        builder.finishSizePrefixed(p)
        val bytes = builder.sizedByteArray()

        var result: LinkPacket? = null
        val job = dispatcherScope.launch(Dispatchers.Unconfined) {
            result = link.receivedPackets.first()
        }
        link.start()
        mockSslSocketReceivedDataStream.write(bytes)

        job.join()
        assert(result!!.isSuccess && result!!.value!!.componentType == Component.DeviceIdentityResponse)
    }

    @Test
    fun `test close emits failure result in start loop and closes the socket`() = runTest {
        val link = Link(mockSSLSocket, dispatcherScope)

        var result: LinkPacket? = null
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

        var result: LinkPacket? = null
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
        every {mockSSLSocket.close()} throws IOException()
        val link = Link(mockSSLSocket, dispatcherScope)
        assertDoesNotThrow {
            link.close()
        }
    }

    @Test
    fun `test close function with socket already closed does not throw exception or try to close the socket again`() = runTest {
        every { mockSSLSocket.isClosed } returns true

        val link = Link(mockSSLSocket, dispatcherScope)
        assertDoesNotThrow {
            link.close()
        }

        verify(exactly = 0) { mockSSLSocket.close() }
    }


}
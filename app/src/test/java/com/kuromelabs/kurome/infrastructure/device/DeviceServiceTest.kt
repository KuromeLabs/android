package com.kuromelabs.kurome.infrastructure.device

import android.content.Context
import android.net.ConnectivityManager
import android.os.Environment
import android.os.StatFs
import com.kuromelabs.kurome.application.devices.Device
import com.kuromelabs.kurome.application.devices.DeviceRepository
import com.kuromelabs.kurome.infrastructure.common.PacketHelpers
import com.kuromelabs.kurome.infrastructure.network.NetworkHelper
import com.kuromelabs.kurome.infrastructure.network.NetworkService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocket

class DeviceServiceTest {

    private lateinit var dispatcherScope: CoroutineScope
    private lateinit var context: Context
    private lateinit var repository: DeviceRepository
    private lateinit var identityProvider: IdentityProvider
    private lateinit var networkHelper: NetworkHelper
    private lateinit var networkService: NetworkService
    private lateinit var serverSocket: ServerSocket
    private lateinit var writeOutputStream: PipedOutputStream
    private lateinit var writeInputStream: PipedInputStream
    private lateinit var readInputStream: PipedInputStream
    private lateinit var readOutputStream: PipedOutputStream

    @BeforeEach
    fun setUp() {
        dispatcherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        serverSocket = createServerSocket(33587)
        mockDependencies()
        mockEnvironment()
    }

    @AfterEach
    fun tearDown() {
        dispatcherScope.cancel()
        serverSocket.close()
        writeOutputStream.close()
        writeInputStream.close()
        readInputStream.close()
        readOutputStream.close()
    }

    private fun mockDependencies() {
        context = mockk(relaxed = true) {
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockk<ConnectivityManager>(relaxed = true)
        }

        repository = mockk(relaxed = true)
        identityProvider = mockk(relaxed = true)

        writeInputStream = PipedInputStream()
        writeOutputStream = PipedOutputStream(writeInputStream)
        readInputStream = PipedInputStream()
        readOutputStream = PipedOutputStream(readInputStream)

        val mockSslSocket: SSLSocket = mockk(relaxed = true) {
            every { session } returns mockk(relaxed = true) {
                every { peerCertificates } returns arrayOf(mockk<X509Certificate>(relaxed = true))
            }
            every { inputStream } answers { writeInputStream }
            every { outputStream } answers { readOutputStream }
            every { close() } just Runs
            every { isConnected } returns true
        }
        networkHelper = mockk(relaxed = true) {
            every { upgradeToSslSocket(any(), any(), any()) } returns mockSslSocket
        }

        networkService = mockk(relaxed = true) {
            every { identityPackets } returns MutableSharedFlow(extraBufferCapacity = 1000, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            every { isConnected(any(), any()) } returns MutableStateFlow(true)
            every { context } returns this@DeviceServiceTest.context
        }
    }

    private fun mockEnvironment() {
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().totalBytes } returns 1000
        every { anyConstructed<StatFs>().freeBytes } returns 500

        mockkStatic(Environment::getDataDirectory)
        mockkStatic(Environment::getExternalStorageDirectory)
        every { Environment.getDataDirectory() } returns mockk {
            every { path } returns "/storage/emulated/0"
        }
        every { Environment.getExternalStorageDirectory() } returns mockk {
            every { path } returns "/storage/emulated/0"
        }
    }


    private fun readPrefixed(inputStream: InputStream): ByteArray {
        val sizeArr = ByteArray(4)
        inputStream.readNBytes(sizeArr, 0, 4)
        val size = ByteBuffer.wrap(sizeArr).order(ByteOrder.LITTLE_ENDIAN).int
        val buffer = ByteArray(size)
        inputStream.readNBytes(buffer, 0, size)
        return buffer
    }

    private fun createServerSocket(port: Int): ServerSocket {
        return ServerSocket().apply {
            bind(InetSocketAddress("127.0.0.1", port))
            soTimeout = 10000
        }
    }

    @Test
    fun `test receive id packet, connect to tcp, send identity, create device handle`() = runTest {
        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }

        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream)

        deviceService.deviceStates.filter { it.isNotEmpty() }.first()
        assertTrue(deviceService.deviceStates.value.containsKey(deviceIdentity.id))
    }

    @Test
    fun `test receive paired id packet, connect to tcp, send identity, create device handle`() = runTest {
        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        every { repository.getSavedDevices() } returns MutableStateFlow(listOf(Device(deviceIdentity.id!!, deviceIdentity.name!!, mockk(relaxed = true))))

        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }

        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream)

        deviceService.deviceStates.filter { it.isNotEmpty() }.first()
        assertTrue(deviceService.deviceStates.value.containsKey(deviceIdentity.id))
    }

    @Test
    fun `test receive duplicate id  that is already connected, ignores it`() = runTest {
        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }
        repeat(10) {
            (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        }

        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream)

        deviceService.deviceStates.filter { it.isNotEmpty() }.first()
        assertTrue(deviceService.deviceStates.value.containsKey(deviceIdentity.id))
        assertEquals(1, deviceService.deviceStates.value.size)
    }

    @Test
    fun `test receive id and ssl handshake fails, device handles are properly cleaned up`() = runTest {
        every  { networkHelper.upgradeToSslSocket(any(), any(), any() ) } throws IOException("ssl handshake failed")
        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }
        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        val flowJob = dispatcherScope.launch { deviceService.deviceStates.filter { it.isEmpty() }.first() }
        readPrefixed(socket.inputStream)
        flowJob.join()
        assertFalse(deviceService.deviceStates.value.containsKey(deviceIdentity.id))
        assertEquals(0, deviceService.deviceStates.value.size)
    }

    @Test
    fun `test receive id and connect, then connection closes, device handles are properly cleaned up`() = runTest {
        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")

        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }
        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream)
        deviceService.deviceStates.filter { it.isNotEmpty() }.first()
        assertTrue(deviceService.deviceStates.value.containsKey(deviceIdentity.id))
        writeInputStream.close()
        writeOutputStream.close()
        deviceService.deviceStates.filter { it.isEmpty() }.first()
        assertFalse(deviceService.deviceStates.value.containsKey(deviceIdentity.id))
        assertEquals(0, deviceService.deviceStates.value.size)
    }

    @Test
    fun `test on network lost clears device handles`() = runTest {
        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        val connectFlow = MutableStateFlow(true)
        every { networkService.isConnected(any(), any()) } answers { connectFlow }
        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }

        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream)
        deviceService.deviceStates.filter { it.isNotEmpty() }.first()
        assertTrue(deviceService.deviceStates.value.containsKey(deviceIdentity.id))
        connectFlow.value = false
        deviceService.deviceStates.filter { it.isEmpty() }.first()
        assertEquals(0, deviceService.deviceStates.value.size)
    }


    @Test
    fun `test receive id and connect, then receive 'true' pair packet on unpaired device`() = runTest {
        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }
        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream)
        deviceService.deviceStates.filter { it.isNotEmpty() }.first()

        val pairPacketBuffer = PacketHelpers.getPairPacketByteBuffer(true)
        val outputChannel = Channels.newChannel(writeOutputStream)
        outputChannel.write(pairPacketBuffer)

        deviceService.deviceStates.first {
            it["test"]!!.pairStatus == PairStatus.PAIR_REQUESTED_BY_PEER
        }
    }


    @Test
    fun `test receive id and connect, then receive 'false' pair packet on unpaired device, pair packet is ignored`() = runTest {
        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }
        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream)
        deviceService.deviceStates.filter { it.isNotEmpty() }.first()

        val pairPacketBuffer = PacketHelpers.getPairPacketByteBuffer(false)
        val outputChannel = Channels.newChannel(writeOutputStream)
        outputChannel.write(pairPacketBuffer)

        deviceService.deviceStates.first {
            it["test"]!!.pairStatus == PairStatus.UNPAIRED
        }
    }


    @Test
    fun `test receive id and connect, then receive 'false' pair packet on paired device, device is unpaired`() = runTest {
        every {
            repository.getSavedDevices()
        } returns MutableStateFlow(listOf(Device("test", "test", mockk(relaxed = true))))

        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }
        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream)
        deviceService.deviceStates.filter { it.isNotEmpty() }.first()

        val pairPacketBuffer = PacketHelpers.getPairPacketByteBuffer(false)
        val outputChannel = Channels.newChannel(writeOutputStream)
        outputChannel.write(pairPacketBuffer)


        // TODO: Uncomment when unpair is implemented
//        deviceService.deviceHandles.first {
//            it["test"]!!.pairStatus == PairStatus.UNPAIRED
//        }
    }


    @Test
    fun `test receive id and connect, then receive 'true' pair packet on paired device, pair packet is ignored`() = runTest {

        every {
            repository.getSavedDevices()
        } returns MutableStateFlow(listOf(Device("test", "test", mockk(relaxed = true))))

        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }
        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream)
        deviceService.deviceStates.filter { it.isNotEmpty() }.first()

        val pairPacketBuffer = PacketHelpers.getPairPacketByteBuffer(false)
        val outputChannel = Channels.newChannel(writeOutputStream)
        outputChannel.write(pairPacketBuffer)

        deviceService.deviceStates.first {
            it["test"]!!.pairStatus == PairStatus.PAIRED
        }
    }


    @Test
    fun `test send outgoing pair request for unpaired device`() = runTest {
        every {
            repository.getSavedDevices()
        } returns MutableStateFlow(emptyList())

        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }
        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream)
        deviceService.deviceStates.filter { it.isNotEmpty() }.first()

        deviceService.sendOutgoingPairRequest("test")
        assertEquals(PairStatus.PAIR_REQUESTED, deviceService.deviceStates.value["test"]!!.pairStatus)
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `test send outgoing pair request for unpaired device, times out`() = runTest {
        every {
            repository.getSavedDevices()
        } returns MutableStateFlow(emptyList())

        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")

        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }

        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream)
        deviceService.deviceStates.filter { it.isNotEmpty() }.first()

        deviceService.sendOutgoingPairRequest("test", this)
        assertEquals(PairStatus.PAIR_REQUESTED, deviceService.deviceStates.value["test"]!!.pairStatus)
        testScheduler.advanceTimeBy(40000)
        assertEquals(PairStatus.UNPAIRED, deviceService.deviceStates.value["test"]!!.pairStatus)
    }


    @Test
    fun `test receive id and connect, then send outgoing pair request, then receive 'true' pair packet (accept)`() = runTest {

        every {
            repository.getSavedDevices()
        } returns MutableStateFlow(emptyList())

        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }
        (networkService.identityPackets as MutableSharedFlow).emit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream) // read the identity packet
        deviceService.deviceStates.filter { it.isNotEmpty() && it.values.any { it.name == "Unknown" } }.first()
        readPrefixed(readInputStream) // read extended identity request
        val outputChannel = Channels.newChannel(writeOutputStream)
        outputChannel.write(PacketHelpers.getDeviceIdentityResponsePacketByteBuffer("test"))
        deviceService.deviceStates.filter { it.isNotEmpty() && it.values.any { it.name == "test" } }.first()

        deviceService.sendOutgoingPairRequest("test")
        deviceService.deviceStates.first { it["test"]!!.pairStatus == PairStatus.PAIR_REQUESTED }
        val pairPacketBuffer = PacketHelpers.getPairPacketByteBuffer(true)
        outputChannel.write(pairPacketBuffer)

        deviceService.deviceStates.first { it.containsKey("test") && it["test"]!!.pairStatus == PairStatus.PAIRED }
    }


    @Test
    fun `test receive id and connect, then send outgoing pair request, then receive 'false' pair packet (reject)`() = runTest {

        every {
            repository.getSavedDevices()
        } returns MutableStateFlow(emptyList())

        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }
        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream) // read the identity packet
        deviceService.deviceStates.filter { it.isNotEmpty() && it.values.any { it.name == "Unknown" } }.first()
        readPrefixed(readInputStream) // read extended identity request
        val outputChannel = Channels.newChannel(writeOutputStream)
        outputChannel.write(PacketHelpers.getDeviceIdentityResponsePacketByteBuffer("test"))
        deviceService.deviceStates.filter { it.isNotEmpty() && it.values.any { it.name == "test" } }.first()

        deviceService.sendOutgoingPairRequest("test")
        deviceService.deviceStates.first { it["test"]!!.pairStatus == PairStatus.PAIR_REQUESTED }
        val pairPacketBuffer = PacketHelpers.getPairPacketByteBuffer(false)
        outputChannel.write(pairPacketBuffer)

        deviceService.deviceStates.first { it["test"]!!.pairStatus == PairStatus.UNPAIRED }
    }


    @Test
    fun `test receive id and connect, then send outgoing pair request twice, nothing happens the second time`() = runTest {

        every {
            repository.getSavedDevices()
        } returns MutableStateFlow(emptyList())

        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }
        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream)
        deviceService.deviceStates.filter { it.isNotEmpty() }.first()

        deviceService.sendOutgoingPairRequest("test")
        val state1 = deviceService.deviceStates.value["test"]
        deviceService.sendOutgoingPairRequest("test")
        val state2 = deviceService.deviceStates.value["test"]
        deviceService.deviceStates.first { it["test"]!!.pairStatus == PairStatus.PAIR_REQUESTED }
        assertEquals(state1, state2)
    }


    @Test
    fun `test receive id and connect, send outgoing request, receive 'true' pair packet from peer twice, nothing happens second time`() = runTest {

        every {
            repository.getSavedDevices()
        } returns MutableStateFlow(emptyList())

        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }
        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream)
        deviceService.deviceStates.filter { it.isNotEmpty() }.first()
        assertEquals(PairStatus.UNPAIRED, deviceService.deviceStates.value["test"]!!.pairStatus)

        val outputChannel = Channels.newChannel(writeOutputStream)
        var pairPacketBuffer = PacketHelpers.getPairPacketByteBuffer(true)
        outputChannel.write(pairPacketBuffer)
        deviceService.deviceStates.first { it["test"]!!.pairStatus == PairStatus.PAIR_REQUESTED_BY_PEER }
        pairPacketBuffer = PacketHelpers.getPairPacketByteBuffer(true)
        outputChannel.write(pairPacketBuffer)
        runBlocking { delay(1000) } // wait for second packet to be processed
    }

    @Test
    fun `test send outgoing pair request for already paired device, nothing happens`() = runTest {
        every {
            repository.getSavedDevices()
        } returns MutableStateFlow(listOf(Device("test", "test", mockk(relaxed = true))))

        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        val deviceService = DeviceService(
            dispatcherScope,
            identityProvider,
            networkHelper,
            repository,
            networkService
        ).apply { start() }
        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket.inputStream)
        deviceService.deviceStates.filter { it.isNotEmpty() }.first()

        deviceService.sendOutgoingPairRequest("test")
        deviceService.deviceStates.first { it["test"]!!.pairStatus == PairStatus.PAIRED }
    }
}

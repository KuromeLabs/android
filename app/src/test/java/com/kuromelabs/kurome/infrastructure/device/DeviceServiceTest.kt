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

    @BeforeEach
    fun setUp() {
        dispatcherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        serverSocket = createServerSocket(33587)
        mockDependencies()
        mockFileSystem()
    }

    @AfterEach
    fun tearDown() {
        dispatcherScope.cancel()
        serverSocket.close()
    }

    // Helper to mock dependencies
    private fun mockDependencies() {
        context = mockk(relaxed = true) {
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockk<ConnectivityManager>(relaxed = true)
        }

        repository = mockk(relaxed = true)
        identityProvider = mockk(relaxed = true)

        networkHelper = mockk(relaxed = true) {
            every { upgradeToSslSocket(any(), any(), any()) } returns mockk(relaxed = true) {
                every { session } returns mockk(relaxed = true) {
                    every { peerCertificates } returns arrayOf(mockk<X509Certificate>(relaxed = true))
                }
            }
        }

        networkService = mockk(relaxed = true) {
            every { identityPackets } returns MutableSharedFlow(extraBufferCapacity = 1000, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            every { isConnected(any(), any()) } returns MutableStateFlow(true)
            every { context } returns this@DeviceServiceTest.context
        }
    }

    // Helper to mock filesystem-related operations
    private fun mockFileSystem() {
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


    // Helper to read prefixed data from a socket
    private fun readPrefixed(socket: Socket): ByteArray {
        val sizeArr = ByteArray(4)
        socket.inputStream.readNBytes(sizeArr, 0, 4)
        val size = ByteBuffer.wrap(sizeArr).order(ByteOrder.LITTLE_ENDIAN).int
        val buffer = ByteArray(size)
        socket.inputStream.readNBytes(buffer, 0, size)
        return buffer
    }

    // Helper to create and bind a server socket
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
        readPrefixed(socket)

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
        readPrefixed(socket)

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
        readPrefixed(socket)

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
        readPrefixed(socket)
        flowJob.join()
        assertFalse(deviceService.deviceStates.value.containsKey(deviceIdentity.id))
        assertEquals(0, deviceService.deviceStates.value.size)
    }

    @Test
    fun `test receive id and connect, then connection closes, device handles are properly cleaned up`() = runTest {
        val writeInputStream = PipedInputStream()
        val mockSslSocket: SSLSocket = mockk(relaxed = true) {
            every { session } returns mockk(relaxed = true) {
                every { peerCertificates } returns arrayOf(mockk<X509Certificate>(relaxed = true))
            }
            every { inputStream } answers { writeInputStream }
            every { close() } just Runs
            every { isConnected } returns true
        }
        networkHelper = mockk(relaxed = true) {
            every { upgradeToSslSocket(any(), any(), any()) } returns mockSslSocket
        }
        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }
        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
        val socket = serverSocket.accept()
        readPrefixed(socket)
        writeInputStream.close()
        deviceService.deviceStates.filter { it.isNotEmpty() }.first()
        assertTrue(deviceService.deviceStates.value.containsKey(deviceIdentity.id))
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
        readPrefixed(socket)

        deviceService.deviceStates.filter { it.isNotEmpty() }.first()
        assertTrue(deviceService.deviceStates.value.containsKey(deviceIdentity.id))
        connectFlow.value = false
        deviceService.deviceStates.filter { it.isEmpty() }.first()
        assertEquals(0, deviceService.deviceStates.value.size)
    }


//    @Test
//    fun `test receive id and connect, then receive pair packet on unpaired device`() = runTest {
//        val writeInputStream = PipedInputStream()
//        val writeOutputStream = PipedOutputStream(writeInputStream)
//        val readInputStream = PipedInputStream()
//        val readOutputStream = PipedOutputStream(readInputStream)
//        val mockSslSocket: SSLSocket = mockk(relaxed = true) {
//            every { session } returns mockk(relaxed = true) {
//                every { peerCertificates } returns arrayOf(mockk<X509Certificate>(relaxed = true))
//            }
//            every { inputStream } answers { writeInputStream }
//            every { outputStream } answers { readOutputStream }
//            every { close() } just Runs
//            every { isConnected } returns true
//        }
//        networkHelper = mockk(relaxed = true) {
//            every { upgradeToSslSocket(any(), any(), any()) } returns mockSslSocket
//        }
//        val deviceIdentity = PacketHelpers.getWindowsDeviceIdentityResponse("test")
//        val deviceService = DeviceService(dispatcherScope, identityProvider, networkHelper, repository, networkService).apply { start() }
//        (networkService.identityPackets as MutableSharedFlow).tryEmit(deviceIdentity)
//        val socket = serverSocket.accept()
//        readPrefixed(socket)
//        val pairPacketBuffer = PacketHelpers.getPairPacketByteBuffer("test")
//        val outputChannel = Channels.newChannel(writeOutputStream)
//        outputChannel.write(pairPacketBuffer)
//        assert(deviceService.deviceStates.value["test"]!!.pairStatus == PairStatus.PAIR_REQUESTED_BY_PEER)
//    }
    
}

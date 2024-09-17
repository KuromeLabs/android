package com.kuromelabs.kurome.infrastructure.network

import Kurome.Fbs.Component
import Kurome.Fbs.CreateDirectoryCommand
import Kurome.Fbs.DeviceIdentityResponse
import Kurome.Fbs.Packet
import Kurome.Fbs.Platform
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkRequest
import com.google.flatbuffers.FlatBufferBuilder
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket

class NetworkServiceTest {
    private lateinit var dispatcherScope: CoroutineScope
    private lateinit var context: Context
    private lateinit var mockSocket: DatagramSocket

    @BeforeEach
    fun setUp() {
        dispatcherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        context = mockk(relaxed=true)
        mockSocket = mockk<DatagramSocket>(relaxed = true) {
            every { isClosed } returns false
            every { close() } answers { callOriginal() }
        }
    }

    @AfterEach
    fun tearDown() {
        dispatcherScope.cancel()
    }

    private fun getNormalDeviceIdentityPacketByteArray(): ByteArray {
        val builder = FlatBufferBuilder(256)
        val p = Packet.createPacket(
            builder,
            Component.DeviceIdentityResponse,
            DeviceIdentityResponse.createDeviceIdentityResponse(builder, 0, 0, builder.createString("test"), builder.createString("test"), builder.createString(""), Platform.Windows, 33587u),
            1
        )
        builder.finishSizePrefixed(p)
        var arr = builder.sizedByteArray()
        arr = arr.sliceArray(4..<arr.size)
        return arr
    }

    private fun getAndroidDeviceIdentityPacketByteArray(): ByteArray {
        val builder = FlatBufferBuilder(256)
        val p = Packet.createPacket(
            builder,
            Component.DeviceIdentityResponse,
            DeviceIdentityResponse.createDeviceIdentityResponse(builder, 0, 0, builder.createString("test"), builder.createString("test"), builder.createString(""), Platform.Android, 33587u),
            1
        )
        builder.finishSizePrefixed(p)
        var arr = builder.sizedByteArray()
        arr = arr.sliceArray(4..<arr.size)
        return arr
    }

    private fun getNonIdentityPacketByteArray(): ByteArray {
        val builder = FlatBufferBuilder(256)
        val p = Packet.createPacket(
            builder,
            Component.CreateDirectoryCommand,
            CreateDirectoryCommand.createCreateDirectoryCommand(builder, builder.createString("test")),
            1
        )
        builder.finishSizePrefixed(p)
        var arr = builder.sizedByteArray()
        arr = arr.sliceArray(4..<arr.size)
        return arr
    }

    @Test
    fun `test startUdpListener with identity packet emits identity packet in flow`(): Unit = runTest{
        val arr = getNormalDeviceIdentityPacketByteArray()
        every { mockSocket.receive(any()) } answers  {
            val packet = it.invocation.args[0] as DatagramPacket
            packet.data = arr
            packet.length = arr.size
        }
        var identityResponse: DeviceIdentityResponse? = null
        val networkService = NetworkService(dispatcherScope, context) { mockSocket }
        val packetJob = dispatcherScope.launch(Dispatchers.Unconfined) {
            identityResponse = networkService.identityPackets.first()
        }
        networkService.startUdpListener()
        packetJob.join()
        assert(identityResponse!!.name == "test")
        assert(identityResponse!!.id == "test")
    }


    @Test
    fun `test startUdpListener with non-identity packet and identity packet with Android platform emits nothing`(): Unit = runTest{
        val identityWindowsArr = getNormalDeviceIdentityPacketByteArray()
        val identityAndroidArr = getAndroidDeviceIdentityPacketByteArray()
        val nonIdentityArr = getNonIdentityPacketByteArray()

        val responses = listOf(nonIdentityArr, identityAndroidArr, identityWindowsArr)
        var callCount = 0

        every { mockSocket.receive(any()) } answers {
            val packet = it.invocation.args[0] as DatagramPacket
            val data = responses.getOrNull(callCount) ?: identityWindowsArr
            packet.data = data
            packet.length = data.size
            callCount++
        }

        val networkService = NetworkService(dispatcherScope, context) { mockSocket }
        val packetJob = dispatcherScope.launch(Dispatchers.Unconfined) {
            networkService.identityPackets.first()
            dispatcherScope.cancel()
        }
        networkService.startUdpListener()
        packetJob.join()
        assert(callCount == 3)
    }


    @Test
    fun `test startUdpListener with closed socket job ends`(): Unit = runTest{
        every { mockSocket.isClosed } returns true
        val networkService = NetworkService(dispatcherScope, context) { mockSocket }
        val job = networkService.startUdpListener()
        job.join()
    }


    @Test
    fun `test startUdpListener with normal identity packet and exception emits nothing`() = runTest {
        val identityWindowsArr = getNormalDeviceIdentityPacketByteArray()
        var callCount = 0

        every { mockSocket.receive(any()) } answers {
            val packet = it.invocation.args[0] as DatagramPacket
            when (callCount++) {
                0 -> throw IOException()
                else -> {
                    packet.data = identityWindowsArr
                    packet.length = identityWindowsArr.size
                }
            }
        }

        val networkService = NetworkService(dispatcherScope, context) { mockSocket }
        val packetJob = dispatcherScope.launch(Dispatchers.Unconfined) {
            networkService.identityPackets.first()
            dispatcherScope.cancel()
        }

        networkService.startUdpListener()
        packetJob.join()

        assertEquals(2, callCount)
    }

    @Test
    fun `test datagramSocket factory function`() {
        val mockContext = mockk<Context>()
        val networkService = spyk(NetworkService(scope = dispatcherScope, context = mockContext))
        val socket = networkService.datagramSocket(0)
        assertEquals(true, socket.reuseAddress)
        assertEquals(true, socket.broadcast)
    }

    @Test
    fun `test isConnected network callback emits true when onAvailable triggers`() = runTest {
        val mockConnectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val mockNetworkRequestBuilder = mockk<NetworkRequest.Builder>()
        val networkRequest = mockk<NetworkRequest>()

        every { mockNetworkRequestBuilder.addTransportType(any()) } returns mockNetworkRequestBuilder
        every { mockNetworkRequestBuilder.build() } returns networkRequest

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager

        val networkCallbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { mockConnectivityManager.registerNetworkCallback(any<NetworkRequest>(), capture(networkCallbackSlot)) } just Runs

        val networkService = NetworkService(dispatcherScope, context)
        val connFlow = networkService.isConnected(mockConnectivityManager, mockNetworkRequestBuilder)

        val flowJob = dispatcherScope.launch(Dispatchers.Unconfined) {
            assert(connFlow.first())
        }

        networkCallbackSlot.captured.onAvailable(mockk())
        flowJob.join()
    }

    @Test
    fun `test isConnected network callback emits false when onLost triggers`() = runTest {
        val mockConnectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val mockNetworkRequestBuilder = mockk<NetworkRequest.Builder>()
        val networkRequest = mockk<NetworkRequest>()

        every { mockNetworkRequestBuilder.addTransportType(any()) } returns mockNetworkRequestBuilder
        every { mockNetworkRequestBuilder.build() } returns networkRequest

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager

        val networkCallbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { mockConnectivityManager.registerNetworkCallback(any<NetworkRequest>(), capture(networkCallbackSlot)) } just Runs

        val networkService = NetworkService(dispatcherScope, context)
        val connFlow = networkService.isConnected(mockConnectivityManager, mockNetworkRequestBuilder)

        val flowJob = dispatcherScope.launch(Dispatchers.Unconfined) {
            assert(!connFlow.first())
        }
        networkCallbackSlot.captured.onLost(mockk())
        flowJob.join()
    }


}
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
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.spy
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket

class NetworkServiceTest {
    private lateinit var dispatcherScope: CoroutineScope
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        dispatcherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        context = mock()
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
            DeviceIdentityResponse.createDeviceIdentityResponse(builder, 0, 0, builder.createString("test"), builder.createString("test"), builder.createString(""), Platform.Windows),
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
            DeviceIdentityResponse.createDeviceIdentityResponse(builder, 0, 0, builder.createString("test"), builder.createString("test"), builder.createString(""), Platform.Android),
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
        val mockSocket = mock<DatagramSocket> {
            on { receive(any()) } doAnswer  {
                val packet = it.getArgument<DatagramPacket>(0)
                packet.data = arr
                packet.length = arr.size
            }
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
        var callCount = 0
        val mockSocket = mock<DatagramSocket> {
            on { receive(any()) } doAnswer  {
                val packet = it.getArgument<DatagramPacket>(0)
                if (callCount == 0) {
                    packet.data = nonIdentityArr
                    packet.length = nonIdentityArr.size
                    callCount++
                    return@doAnswer
                } else if (callCount == 1) {
                    packet.data = identityAndroidArr
                    packet.length = identityAndroidArr.size
                    callCount++
                    return@doAnswer
                }
                callCount++
                packet.data = identityWindowsArr
                packet.length = identityWindowsArr.size
            }
        }

        val networkService = NetworkService(dispatcherScope, context) { mockSocket }
        val packetJob = dispatcherScope.launch(Dispatchers.Unconfined) {
            networkService.identityPackets.first()
            dispatcherScope.cancel()
        }
        networkService.startUdpListener()
        packetJob.join()
        // receive was called three times even though we only got one packet
        assert(callCount == 3)
    }


    @Test
    fun `test startUdpListener with closed socket job ends`(): Unit = runTest{
        val mockSocket = mock<DatagramSocket> {
            on { isClosed }.doReturn(true)
        }

        val networkService = NetworkService(dispatcherScope, context) { mockSocket }
        val job = networkService.startUdpListener()
        job.join()
    }


    @Test
    fun `test startUdpListener with normal identity packet and exception emits nothing`(): Unit = runTest{
        val identityWindowsArr = getNormalDeviceIdentityPacketByteArray()
        var callCount = 0
        val mockSocket = mock<DatagramSocket> {
            on { receive(any()) } doAnswer  {
                val packet = it.getArgument<DatagramPacket>(0)
                if (callCount == 0) {
                    callCount++
                    throw IOException()
                }
                callCount++
                packet.data = identityWindowsArr
                packet.length = identityWindowsArr.size
            }
        }

        val networkService = NetworkService(dispatcherScope, context) { mockSocket }
        val packetJob = dispatcherScope.launch(Dispatchers.Unconfined) {
            networkService.identityPackets.first()
            dispatcherScope.cancel()
        }
        networkService.startUdpListener()
        packetJob.join()
        // receive was called two times even though we only got one packet
        assert(callCount == 2)
    }


    @Test
    fun `test datagramSocket factory function`() {
        val mockContext = mock<Context>()
        val networkService = spy(NetworkService(scope = dispatcherScope, context = mockContext))
        val socket = networkService.datagramSocket(0)
        assertEquals(true, socket.reuseAddress)
        assertEquals(true, socket.broadcast)
    }

    @Test
    fun `test isConnected network callback emits true when onAvailable triggers`(): Unit = runTest{
        val mockConnectivityManager = mock<ConnectivityManager>()
        val mockNetworkRequestBuilder: NetworkRequest.Builder = mock()
        val networkRequest: NetworkRequest = mock()

        whenever(mockNetworkRequestBuilder.addTransportType(any())).thenReturn(mockNetworkRequestBuilder)
        whenever(mockNetworkRequestBuilder.build()).thenReturn(networkRequest)

        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(mockConnectivityManager)
        val networkCallbackCaptor = argumentCaptor<ConnectivityManager.NetworkCallback>()
        doNothing().whenever(mockConnectivityManager).registerNetworkCallback(any<NetworkRequest>(), networkCallbackCaptor.capture())
        val networkService = NetworkService(dispatcherScope, context)

        val connFlow = networkService.isConnected(mockConnectivityManager, mockNetworkRequestBuilder)
        val flowJob = dispatcherScope.launch(Dispatchers.Unconfined) {
            assert(connFlow.first())
        }

        networkCallbackCaptor.firstValue.onAvailable(mock())
        flowJob.join()
    }

    @Test
    fun `test isConnected network callback emits false when onLost triggers`(): Unit = runTest{
        val mockConnectivityManager = mock<ConnectivityManager>()
        val mockNetworkRequestBuilder: NetworkRequest.Builder = mock()
        val networkRequest: NetworkRequest = mock()

        whenever(mockNetworkRequestBuilder.addTransportType(any())).thenReturn(mockNetworkRequestBuilder)
        whenever(mockNetworkRequestBuilder.build()).thenReturn(networkRequest)

        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(mockConnectivityManager)
        val networkCallbackCaptor = argumentCaptor<ConnectivityManager.NetworkCallback>()
        doNothing().whenever(mockConnectivityManager).registerNetworkCallback(any<NetworkRequest>(), networkCallbackCaptor.capture())
        val networkService = NetworkService(dispatcherScope, context)

        val connFlow = networkService.isConnected(mockConnectivityManager, mockNetworkRequestBuilder)
        val flowJob = dispatcherScope.launch(Dispatchers.Unconfined) {
            assert(!connFlow.first())
        }

        networkCallbackCaptor.firstValue.onLost(mock())
        flowJob.join()
    }

}
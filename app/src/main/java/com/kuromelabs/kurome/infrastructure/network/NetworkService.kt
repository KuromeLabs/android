package com.kuromelabs.kurome.infrastructure.network

import Kurome.Fbs.Component
import Kurome.Fbs.DeviceIdentityResponse
import Kurome.Fbs.Packet
import Kurome.Fbs.Platform
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class NetworkService(
    private var scope: CoroutineScope,
    var context: Context,

    val datagramSocket: (udpListenPort :Int) -> DatagramSocket = { udpListenPort ->
        DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(udpListenPort))
        }
    }

) {
    private val _identityPackets = MutableSharedFlow<DeviceIdentityResponse>(extraBufferCapacity = 1000, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val identityPackets: SharedFlow<DeviceIdentityResponse> = _identityPackets

    fun isConnected(cm: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager, requestBuilder: NetworkRequest.Builder = NetworkRequest.Builder()): Flow<Boolean> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                trySend(true)

            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        val networkRequest = requestBuilder
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()
        cm.registerNetworkCallback(networkRequest, networkCallback)

        awaitClose { cm.unregisterNetworkCallback(networkCallback) }
    }


    fun startUdpListener() = scope.launch {
        datagramSocket(33586).use { udpSocket ->
//            Timber.d("UDP Socket bound to port $udpListenPort")
            while (scope.coroutineContext.isActive && !udpSocket.isClosed) {
                try {
                        val buffer = ByteArray(1024)
                        val udpPacket = DatagramPacket(buffer, buffer.size)
                        udpSocket.receive(udpPacket)
                        val messageBytes = ByteBuffer.wrap(udpPacket.data, udpPacket.offset, udpPacket.length)
                        val packet = Packet.getRootAsPacket(messageBytes)
                        if (packet.componentType != Component.DeviceIdentityResponse)
                            continue
                        val deviceIdentityResponse = packet.component(DeviceIdentityResponse()) as DeviceIdentityResponse
                        if (deviceIdentityResponse.platform == Platform.Android)
                            continue
                        _identityPackets.emit(deviceIdentityResponse)
                } catch (e: Exception) {
                    Timber.d("Exception at NetworkService: $e")
                }
            }
        }
    }
}
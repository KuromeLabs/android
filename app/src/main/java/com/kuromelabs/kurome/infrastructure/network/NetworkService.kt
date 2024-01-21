package com.kuromelabs.kurome.infrastructure.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.kuromelabs.kurome.infrastructure.device.DeviceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket

class NetworkService(
    var scope: CoroutineScope,
    var context: Context,
    var deviceService: DeviceService
) {
    private val udpListenPort = 33586

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager


    private fun registerNetworkCallback(networkCallback: ConnectivityManager.NetworkCallback) {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()


        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

    }

    fun unregisterNetworkCallback(networkCallback: ConnectivityManager.NetworkCallback) {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            // Called when a network is available

        }

        override fun onLost(network: Network) {
            // Called when a network is lost
//
            deviceService.onNetworkLost()
        }
    }

    fun start() {
        registerNetworkCallback(networkCallback)
        startUdpListener()
    }

    suspend fun createServerLink(client: Socket): Link {
        TODO("Not yet implemented")
    }


    private fun startUdpListener() = scope.launch {
        val udpSocket = DatagramSocket(null)
        Timber.d("Setting up socket")
        udpSocket.reuseAddress = true
        udpSocket.broadcast = true
        udpSocket.bind(InetSocketAddress(udpListenPort))
        Timber.d("Socket bound to port $udpListenPort")
        while (scope.coroutineContext.isActive && !udpSocket.isClosed) {
            try {
                if (!udpSocket.isClosed) {
                    val buffer = ByteArray(1024)
                    val packet = DatagramPacket(buffer, buffer.size)
//                        Timber.d("Waiting for incoming UDP")
                    udpSocket.receive(packet)
                    val message = String(packet.data, packet.offset, packet.length)
                    Timber.d("received UDP: $message")
                    val strs = message.split(':')
                    val id = strs[3]
                    val ip = strs[1]
                    val name = strs[2]

                    deviceService.handleUdp(name, id, ip, 33587)

                }
            } catch (e: Exception) {
                Timber.d("Exception at UdpListenerService: $e")
            }
        }
        udpSocket.close()
    }
}
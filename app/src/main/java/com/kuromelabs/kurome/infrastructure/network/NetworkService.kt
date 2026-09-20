package com.kuromelabs.kurome.infrastructure.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.DiscoveryRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * Finds Kurome peers on the LAN using mDNS.
 */
class NetworkService(
    private var scope: CoroutineScope,
    var context: Context,
    nsdManagerProvider: () -> NsdManager = {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
) {
    private val nsdManager by lazy(nsdManagerProvider)

    private val _discoveredDevices = MutableStateFlow<Map<String, DiscoveredDevice>>(emptyMap())

    val discoveredDevices: StateFlow<Map<String, DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val claimedServiceNames = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val resolveMutex = Mutex()

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

    @Synchronized
    fun startDiscovery() {
        if (discoveryListener != null) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Timber.d("mDNS discovery started for $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Timber.d("mDNS discovery stopped for $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.e("mDNS discovery failed to start: $errorCode")
                onListenerDead(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.e("mDNS discovery failed to stop: $errorCode")
                onListenerDead(this)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val serviceName = serviceInfo.serviceName ?: return
                if (!claimedServiceNames.add(serviceName)) return
                Timber.d("mDNS found $serviceName")
                scope.launch { onServiceFound(serviceName, serviceInfo) }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val serviceName = serviceInfo.serviceName ?: return
                if (!claimedServiceNames.remove(serviceName)) return
                Timber.d("mDNS lost $serviceName")
                _discoveredDevices.update { devices ->
                    devices.filterValues { it.serviceName != serviceName }
                }
            }
        }
        discoveryListener = listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            val request = DiscoveryRequest.Builder(SERVICE_TYPE)
                .setFlags(DiscoveryRequest.FLAG_NO_PICKER)
                .setDisplayNameAttribute("name")
                .build()
            nsdManager.discoverServices(request, { it.run() }, listener)
        } else {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    @Synchronized
    fun stopDiscovery() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        claimedServiceNames.clear()
        _discoveredDevices.value = emptyMap()
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            Timber.d("Exception stopping mDNS discovery: $e")
        }
    }

    fun restartDiscovery() {
        stopDiscovery()
        startDiscovery()
    }

    @Synchronized
    private fun onListenerDead(listener: NsdManager.DiscoveryListener) {
        if (discoveryListener === listener) discoveryListener = null
    }

    private suspend fun onServiceFound(serviceName: String, serviceInfo: NsdServiceInfo) {
        val device = try {
            resolve(serviceInfo)?.let { toDiscoveredDevice(it) }
        } catch (e: Exception) {
            Timber.d("Exception resolving $serviceName: $e")
            null
        }

        if (device == null) {
            // Unclaim after a pause rather than immediately: the next response packet for this
            // service is usually already queued, and retrying on it would restart the flood.
            delay(RESOLVE_RETRY_BACKOFF_MS.milliseconds)
            claimedServiceNames.remove(serviceName)
            return
        }

        Timber.d("mDNS resolved ${device.name} (${device.id}) at ${device.addresses}:${device.port}")
        _discoveredDevices.update { it + (device.id to device) }
    }

    private suspend fun resolve(serviceInfo: NsdServiceInfo): NsdServiceInfo? =
        resolveMutex.withLock {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS.milliseconds) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) resolveWithCallback(serviceInfo)
                else resolveLegacy(serviceInfo)
            }
        }

    /**
     * API 34+ replaced the one-shot resolve with a subscription. We only want the first update, so
     * unregister as soon as one arrives.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun resolveWithCallback(serviceInfo: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            var callback: NsdManager.ServiceInfoCallback? = null
            fun unregister() {
                val registered = callback ?: return
                callback = null
                try {
                    nsdManager.unregisterServiceInfoCallback(registered)
                } catch (e: Exception) {
                    Timber.d("Exception unregistering service info callback: $e")
                }
            }
            callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    Timber.d("Service info callback registration failed: $errorCode")
                    callback = null
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    unregister()
                    if (continuation.isActive) continuation.resume(serviceInfo)
                }

                override fun onServiceLost() {
                    unregister()
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            nsdManager.registerServiceInfoCallback(serviceInfo, { it.run() }, callback!!)
            continuation.invokeOnCancellation { unregister() }
        }

    @Suppress("DEPRECATION")
    private suspend fun resolveLegacy(serviceInfo: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Timber.d("Failed to resolve ${serviceInfo.serviceName}: $errorCode")
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (continuation.isActive) continuation.resume(serviceInfo)
                }
            })
        }

    private fun toDiscoveredDevice(serviceInfo: NsdServiceInfo): DiscoveredDevice? {
        val attributes = serviceInfo.attributes
        fun attribute(key: String) = attributes[key]?.toString(Charsets.UTF_8)

        // The phone never mounts another phone, and it would otherwise answer its own advertisement
        // if we ever start advertising from this side.
        val platform = attribute(KEY_PLATFORM)
        if (platform.equals("Android", ignoreCase = true)) {
            Timber.d("Ignoring Android peer ${serviceInfo.serviceName}")
            return null
        }

        val id = attribute(KEY_ID) ?: serviceInfo.serviceName
        if (id == null) {
            Timber.d("Ignoring peer with no id and no service name, TXT keys: ${attributes.keys}")
            return null
        }

        val addresses = hostAddresses(serviceInfo)
        if (addresses.isEmpty() || serviceInfo.port <= 0) {
            Timber.d(
                "Ignoring peer $id: resolved to addresses=$addresses port=${serviceInfo.port} " +
                    "(platform=$platform, TXT keys: ${attributes.keys})"
            )
            return null
        }

        return DiscoveredDevice(
            id = id,
            name = attribute(KEY_NAME) ?: serviceInfo.serviceName ?: id,
            addresses = addresses,
            port = serviceInfo.port,
            serviceName = serviceInfo.serviceName,
        )
    }

    @Suppress("DEPRECATION")
    private fun hostAddresses(serviceInfo: NsdServiceInfo): List<String> {
        val addresses =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) serviceInfo.hostAddresses
            else listOfNotNull(serviceInfo.host)
        return addresses
            .sortedBy { it !is Inet4Address }
            .mapNotNull { it.toConnectableAddress() }
            .distinct()
    }

    private fun InetAddress.toConnectableAddress(): String? {
        if (isAnyLocalAddress || isLoopbackAddress || isMulticastAddress) return null
        return hostAddress
    }

    private companion object {
        /** Must match the Windows worker's `ServiceProfile` type. */
        const val SERVICE_TYPE = "_kurome._tcp."
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_PLATFORM = "platform"

        const val RESOLVE_TIMEOUT_MS = 10_000L
        const val RESOLVE_RETRY_BACKOFF_MS = 15_000L
    }
}

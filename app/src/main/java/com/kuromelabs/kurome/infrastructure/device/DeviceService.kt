package com.kuromelabs.kurome.infrastructure.device

import com.kuromelabs.core.models_fbs.*
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.application.devices.Device
import com.kuromelabs.kurome.application.devices.DeviceRepository
import com.kuromelabs.kurome.infrastructure.network.DiscoveredDevice
import com.kuromelabs.kurome.infrastructure.network.Link
import com.kuromelabs.kurome.infrastructure.network.NetworkHelper
import com.kuromelabs.kurome.infrastructure.network.NetworkService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.channels.Channels
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.net.ssl.*
import kotlin.time.Duration.Companion.milliseconds

class DeviceService @Inject constructor(
    private val scope: CoroutineScope,
    private val identityProvider: IdentityProvider,
    private val networkHelper: NetworkHelper,
    private val deviceRepository: DeviceRepository,
    private val networkService: NetworkService
) {

    private val _deviceHandles: MutableMap<String, DeviceHandle> = ConcurrentHashMap<String, DeviceHandle>()

    private val _deviceStates = MutableStateFlow(emptyMap<String, DeviceState>())
    val deviceStates = _deviceStates.asStateFlow()

    private val savedDevicesFlow = deviceRepository.getSavedDevices()
        .map { devices -> devices.associateBy { it.id } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun start() {
        scope.launch {
            savedDevicesFlow.filterNotNull().first()
            observeNetworkState()
            networkService.startDiscovery()
        }
    }

    fun stop() {
        networkService.stopDiscovery()
        _deviceHandles.forEach { (_, handle) -> handle.stop() }
        _deviceHandles.clear()
        _deviceStates.value = emptyMap()
    }

    private fun observeNetworkState() {
        var advertised = emptySet<String>()
        networkService.discoveredDevices
            .onEach { devices ->
                (advertised - devices.keys).forEach { requestLivenessProbe(it) }
                advertised = devices.keys
                devices.values.forEach { connect(it) }
            }
            .launchIn(scope)

        networkService.isConnected()
            .debounce(NETWORK_SETTLE_MS.milliseconds)
            .onEach { isConnected ->
                if (isConnected) networkService.restartDiscovery() else onNetworkLost()
            }
            .launchIn(scope)

        // mDNS announces once and then stays quiet, unlike the UDP broadcast it replaced, so a peer
        // whose link dropped while it is still advertising has to be retried on a timer. The same
        // tick revives discovery if NsdManager dropped it without a connectivity event to match.
        scope.launch {
            while (isActive) {
                delay(RETRY_INTERVAL_MS.milliseconds)
                networkService.startDiscovery()
                networkService.discoveredDevices.value.values.forEach { connect(it) }
            }
        }
    }

    /** Connects to a peer entered by hand, for networks where mDNS does not make it across. */
    fun connectManually(ip: String, port: Int) {
        connect(
            DiscoveredDevice(
                id = MANUAL_DEVICE_ID,
                name = "ManuallyConnectDevice",
                addresses = listOf(ip),
                port = port,
            )
        )
    }

    fun connect(discovered: DiscoveredDevice) {
        val id = discovered.id
        val device = savedDevicesFlow.value?.get(id)
        val trusted = device?.certificate != null

        // putIfAbsent, not a containsKey check: discovery and the retry ticker both land here.
        if (!addHandle(DeviceHandle(trusted, "Unknown", id, null))) return

        Timber.d("Connecting to $id (${discovered.name}) at ${discovered.addresses}:${discovered.port}")
        scope.launch {
            val result = connectToDevice(discovered, device)
            handleConnection(result, id, discovered)
        }
    }

    private suspend fun connectToDevice(
        discovered: DiscoveredDevice,
        device: Device?
    ): Result<SSLSocket> {
        return withContext(Dispatchers.IO) {
            var lastFailure: Throwable = IOException("No reachable address for ${discovered.id}")
            // A Windows peer advertises every NIC it has, and the Hyper-V/VPN ones do not route
            // back to the phone, so walk the list until one answers.
            for (ip in discovered.addresses) {
                val socket = Socket().apply { reuseAddress = true }
                try {
                    Timber.d("Connecting to $ip:${discovered.port}")
                    socket.connect(InetSocketAddress(ip, discovered.port), CONNECT_TIMEOUT_MS)
                    sendIdentity(socket)
                    Timber.d("Upgrading to SSL for $ip:${discovered.port}")
                    return@withContext Result.success(
                        networkHelper.upgradeToSslSocket(socket, true, device?.certificate)
                    )
                } catch (e: Exception) {
                    socket.close()
                    Timber.e("Connection error for $ip:${discovered.port}: $e")
                    lastFailure = e
                }
            }
            Result.failure(lastFailure)
        }
    }

    private fun sendIdentityQuery(handle: DeviceHandle, packetId: Long = 0) {
        val builder = FlatBufferBuilder(256)
        DeviceIdentityQuery.startDeviceIdentityQuery(builder)
        val query = DeviceIdentityQuery.endDeviceIdentityQuery(builder)
        val packet = Packet.createPacket(builder, Component.DeviceIdentityQuery, query, packetId)
        builder.finishSizePrefixed(packet)
        handle.sendPacket(builder.dataBuffer())
    }

    private suspend fun handleConnection(
        result: Result<SSLSocket>,
        id: String,
        discovered: DiscoveredDevice
    ) {
        result.onSuccess { sslSocket ->
            val handle = _deviceHandles[id] ?: return@onSuccess
            updateHandle(id) {
                it.name = "Unknown"
                it.certificate = sslSocket.session.peerCertificates[0] as X509Certificate
                it.link = Link(sslSocket, it.localScope)
                it
            }
            Timber.d("Connected to ${sslSocket.inetAddress?.hostAddress}:${discovered.port}, id: $id. Getting extended identity...")
            var identityPacket: Packet? = null
            // UNDISPATCHED so the body runs inline up to its first real suspension, which is the
            // receivedPackets subscription. A plain launch might not have subscribed before
            // link.start() below begins reading, and the reply would be lost to the 35s timeout.
            val identityJob = handle.localScope.launch(start = CoroutineStart.UNDISPATCHED) {
                identityPacket = handle.getIncomingPacketWithId(0, 35000)
            }
            handle.reloadPlugins(identityProvider)
            sendIdentityQuery(handle)
            handle.link!!.start()
            identityJob.join()
            if (identityPacket == null) {
                Timber.e("Failed to get extended identity")
                onDeviceDisconnected(id)
                return@onSuccess
            }
            val identity = identityPacket!!.component(DeviceIdentityResponse()) as DeviceIdentityResponse

            updateHandle(id) {
                it.name = identity.name!!
                it.localScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    observeDevicePackets(it.link!!, id)
                }
                it.localScope.launch { observeLinkLiveness(id) }
                it.localScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    var previous: PairStatus? = null
                    it.pairHandler.pairStatus.collect { status ->
                        Timber.d("Pair status changed to $status")
                        val isTransition = previous != null && previous != status
                        previous = status
                        onPairStatusChanged(status, id, isTransition)
                    }
                }
                it
            }
        }.onFailure {
            onDeviceDisconnected(id)
            Timber.e("Failed to connect to ${discovered.addresses}:${discovered.port}")
        }
    }

    private suspend fun observeDevicePackets(link: Link, handleId: String) {
        link.receivedPackets
            .collect { packetResult ->
                if (packetResult.isFailure) {
                    packetResult.onFailure { onDeviceDisconnected(handleId) }
                } else {
                    _deviceHandles[handleId]?.lastPacketReceivedAt = SystemClock.elapsedRealtime()
                }
            }
    }

    private fun requestLivenessProbe(handleId: String) {
        val handle = _deviceHandles[handleId] ?: return
        Timber.d("$handleId stopped advertising, probing the link")
        handle.livenessProbeRequests.trySend(Unit)
    }

    private suspend fun observeLinkLiveness(handleId: String) {
        var probeNow = false
        while (currentCoroutineContext().isActive) {
            val handle = _deviceHandles[handleId] ?: return
            if (!probeNow) {
                val idleFor = SystemClock.elapsedRealtime() - handle.lastPacketReceivedAt
                if (idleFor < HEARTBEAT_IDLE_MS) {
                    probeNow = withTimeoutOrNull((HEARTBEAT_IDLE_MS - idleFor).milliseconds) {
                        handle.livenessProbeRequests.receive()
                        true
                    } == true
                    continue
                }
            }
            probeNow = false

            val packetId = heartbeatPacketId.decrementAndGet()
            val reply = coroutineScope {
                val awaitReply = async(start = CoroutineStart.UNDISPATCHED) {
                    handle.getIncomingPacketWithId(packetId, HEARTBEAT_TIMEOUT_MS)
                }
                Timber.d("Heartbeat request")
                sendIdentityQuery(handle, packetId)
                awaitReply.await()
            }
            Timber.d("Heartbeat response")

            if (reply == null) {
                Timber.w("Heartbeat timed out for $handleId, dropping the link")
                onDeviceDisconnected(handleId)
                return
            }
            handle.lastPacketReceivedAt = SystemClock.elapsedRealtime()
        }
    }

    private suspend fun onPairStatusChanged(
        pairStatus: PairStatus,
        handleId: String,
        isTransition: Boolean
    ) {
        val handle = _deviceHandles[handleId] ?: return
        updateHandle(handleId) {
            handle
        }
        when (pairStatus) {
            PairStatus.PAIRED -> {
                Timber.d("Device $handleId paired")
                deviceRepository.insert(Device(handle.id, handle.name, handle.certificate))
            }
            PairStatus.UNPAIRED -> if (isTransition) {
                Timber.d("Device $handleId unpaired")
                deviceRepository.delete(handle.id)
            }

            PairStatus.PAIR_REQUESTED -> {}
            PairStatus.PAIR_REQUESTED_BY_PEER -> {}
        }

        if (isTransition) {
            try {
                handle.reloadPlugins(identityProvider)
            } catch (e: Exception) {
                Timber.e(e, "Failed to reload plugins for $handleId after pair status change")
            }
        }
    }

    private fun onDeviceDisconnected(id: String) {
        _deviceHandles[id]?.stop()
        _deviceHandles.remove(id)
        _deviceStates.update { it.toMutableMap().apply { remove(id) } }
    }

    private fun updateHandle(id: String, action: (handle: DeviceHandle) -> DeviceHandle) {
        val current = _deviceHandles[id] ?: return
        val updated = action(current)
        if (_deviceHandles.replace(id, updated) == null) return
        _deviceStates.update {
            it.toMutableMap().apply {
                this[id] = DeviceState(updated.name, id, updated.pairHandler.pairStatus.value, true)
            }
        }
    }

    /** Returns false if a handle for this device already exists, meaning the caller should back off. */
    private fun addHandle(handle: DeviceHandle): Boolean {
        return _deviceHandles.putIfAbsent(handle.id, handle) == null
    }

    private fun sendIdentity(socket: Socket) {
        val builder = FlatBufferBuilder(256)
        val id = identityProvider.getEnvironmentId()
        val name = identityProvider.getEnvironmentName()
        val statFs = StatFs(Environment.getDataDirectory().path)

        val response = DeviceIdentityResponse.createDeviceIdentityResponse(
            builder,
            statFs.totalBytes,
            statFs.freeBytes,
            builder.createString(name),
            builder.createString(id),
            builder.createString(""),
            Platform.Android,
            0u
        )

        val packet = Packet.createPacket(builder, Component.DeviceIdentityResponse, response, -1)
        builder.finishSizePrefixed(packet)

        Channels.newChannel(socket.getOutputStream()).write(builder.dataBuffer())
    }

    private fun onNetworkLost() {
        Timber.d("Network lost")
        _deviceHandles.forEach { (_, handle) -> handle.stop() }
        _deviceHandles.clear()
        _deviceStates.value = emptyMap()
    }

    fun sendOutgoingPairRequest(id: String) {
        val handle = _deviceHandles[id] ?: return
        scope.launch { handle.pairHandler.sendOutgoingPairRequest() }
    }

    fun unpairDevice(id: String) {
        scope.launch {
            _deviceHandles[id]?.pairHandler?.sendUnpairRequest()
            deviceRepository.delete(id)
        }
    }

    private val heartbeatPacketId = AtomicLong(HEARTBEAT_ID_BASE)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3000
        const val HEARTBEAT_IDLE_MS = 60_000L
        const val HEARTBEAT_TIMEOUT_MS = 10_000L
        const val HEARTBEAT_ID_BASE = -1000L
        const val NETWORK_SETTLE_MS = 750L
        const val RETRY_INTERVAL_MS = 10_000L

        /** Manual connections have no id until the peer's identity arrives; see connectManually. */
        const val MANUAL_DEVICE_ID = "ManuallyConnectedDeviceId"
    }
}

package com.kuromelabs.kurome.infrastructure.device

import Kurome.Fbs.*
import android.os.Environment
import android.os.StatFs
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.application.devices.Device
import com.kuromelabs.kurome.application.devices.DeviceRepository
import com.kuromelabs.kurome.infrastructure.network.Link
import com.kuromelabs.kurome.infrastructure.network.NetworkHelper
import com.kuromelabs.kurome.infrastructure.network.NetworkService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.channels.Channels
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.net.ssl.*

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
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun start() {
        observeNetworkState()
        networkService.startUdpListener()
    }

    private fun observeNetworkState() {
        networkService.identityPackets
            .onEach { packet -> handleUdpPacket(packet) }
            .launchIn(scope)

        networkService.isConnected()
            .onEach { isConnected -> if (!isConnected) onNetworkLost() }
            .launchIn(scope)
    }

    private fun handleUdpPacket(packet: DeviceIdentityResponse) {
        val name = packet.name!!
        val id = packet.id!!
        val ip = packet.localIp
        val port = packet.tcpListeningPort
        Timber.d("Received UDP packet from $ip:$port, id: $id, name: $name")
        if (deviceStates.value.containsKey(id)) return

        val device = savedDevicesFlow.value[id]
        val pairStatus = if (device?.certificate != null) PairStatus.PAIRED else PairStatus.UNPAIRED

        val deviceHandle = DeviceHandle(pairStatus, name, id, null)
        addHandle(deviceHandle)
        scope.launch {
            val result = connectToDevice(ip!!, port.toInt(), device)
            handleConnection(result, id, ip, port.toInt(), name)
        }
    }

    private suspend fun connectToDevice(ip: String, port: Int, device: Device?): Result<SSLSocket> {
        return withContext(Dispatchers.IO) {
            val socket = Socket().apply { reuseAddress = true }
            try {
                Timber.d("Connecting to $ip:$port")
                socket.connect(InetSocketAddress(ip, port), 3000)
                sendIdentity(socket)
                Timber.d("Upgrading to SSL for $ip:$port")
                Result.success(networkHelper.upgradeToSslSocket(socket, true, device?.certificate))
            } catch (e: Exception) {
                socket.close()
                Timber.e("Connection error: $e")
                Result.failure(e)
            }
        }
    }

    private fun handleConnection(
        result: Result<SSLSocket>,
        id: String,
        ip: String,
        port: Int,
        name: String
    ) {
        result.onSuccess { sslSocket ->
            Timber.d("Connected to $ip:$port, name: $name, id: $id")
            _deviceHandles[id]!!.start()
            updateHandle(id) {
                it.copy(name = name, certificate = sslSocket.session.peerCertificates[0] as X509Certificate).apply {
                    this.link = Link(sslSocket, this.localScope)
                }
            }
                it.reloadPlugins(identityProvider)
                it.localScope.launch(Dispatchers.Unconfined) {
                    observeDevicePackets(it.link!!, id)
                }
                it
            }
        }.onFailure {
            onDeviceDisconnected(id)
            Timber.e("Failed to connect to $ip:$port")
        }
    }

    private suspend fun observeDevicePackets(link: Link, handleId: String) {
        link.receivedPackets
            .filter { it.isFailure || (it.isSuccess && it.value!!.componentType == Component.Pair) }
            .collect { packetResult ->
                packetResult.onSuccess { handlePairPacket(it.component(Pair()) as Pair, handleId) }
                    .onFailure { onDeviceDisconnected(handleId) }
            }
    }

    private suspend fun handlePairPacket(pair: Pair, handleId: String) {
        val handle = _deviceHandles[handleId] ?: return
        when {
            pair.value && handle.pairStatus == PairStatus.UNPAIRED -> {
                Timber.d("Received pair request")
                updateHandle(handleId) {
                    it.pairStatus = PairStatus.PAIR_REQUESTED_BY_PEER
                    it
                }
            }
            pair.value && handle.pairStatus == PairStatus.PAIR_REQUESTED -> {
                Timber.d("Pair request accepted by peer ${handle.id}, saving")
                updateHandle(handleId) {
                    it.pairStatus = PairStatus.PAIRED
                    it
                }
                deviceRepository.insert(Device(handle.id, handle.name, handle.certificate))
                handle.reloadPlugins(identityProvider)
            }
            !pair.value && handle.pairStatus == PairStatus.PAIR_REQUESTED -> {
                Timber.d("Pair request rejected")
                updateHandle(handleId) {
                    it.pairStatus = PairStatus.UNPAIRED
                    it
                }
                handle.outgoingPairRequestTimerJob?.cancel()
            }
            else -> Timber.d("Pair request in unexpected state: ${handle.pairStatus}")
        }
    }

    private fun onDeviceDisconnected(id: String) {
        _deviceHandles[id]?.stop()
        _deviceHandles.remove(id)
        _deviceStates.update { it.toMutableMap().apply { remove(id) } }
    }

    private fun updateHandle(id: String, action: (handle: DeviceHandle) -> DeviceHandle) {
        _deviceHandles.put(id, action(_deviceHandles[id]!!))
        _deviceStates.update {
            it.toMutableMap().apply {
                this[id] = DeviceState(_deviceHandles[id]!!.name, id, _deviceHandles[id]!!.pairStatus, true)
            }
        }
    }

    private fun addHandle(handle: DeviceHandle) {
        _deviceHandles[handle.id] = handle
        _deviceStates.update {
            it.toMutableMap().apply {
                this[handle.id] = DeviceState(handle.name, handle.id, handle.pairStatus, true)
            }
        }
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
        _deviceHandles.forEach { (id, handle) ->
            handle.stop()
        }
        _deviceHandles.clear()
    }

    fun sendOutgoingPairRequest(id: String, scope: CoroutineScope? = null) {
        if (!deviceStates.value.containsKey(id)) return
        if (deviceStates.value[id]!!.pairStatus == PairStatus.PAIRED ||
            deviceStates.value[id]!!.pairStatus == PairStatus.PAIR_REQUESTED) {
            Timber.d("Pair request already in progress")
            return
        }

        updateHandle(id) { handle ->
            handle.copy(pairStatus = PairStatus.PAIR_REQUESTED).apply {
                this.outgoingPairRequestTimerJob = (scope ?: this.localScope).launch {
                    delay(30000)
                    Timber.d("Pair request timed out")
                    updateHandle(id) {
                        it.copy(pairStatus = PairStatus.UNPAIRED).apply { it.outgoingPairRequestTimerJob = null }
                    }
                }
            }
            handle.pairStatus = PairStatus.PAIR_REQUESTED
            handle.outgoingPairRequestTimerJob = (scope ?: handle.localScope).launch {
                delay(30000)
                Timber.d("Pair request timed out")
                updateHandle(id) {
                    it.pairStatus = PairStatus.UNPAIRED
                    it.outgoingPairRequestTimerJob = null
                    it
                }
            }
            handle
        }

        val builder = FlatBufferBuilder(256)
        val pair = Pair.createPair(builder, true)
        val packet = Packet.createPacket(builder, Component.Pair, pair, -126)
        builder.finishSizePrefixed(packet)
        _deviceHandles[id]!!.sendPacket(builder.dataBuffer())
    }

}

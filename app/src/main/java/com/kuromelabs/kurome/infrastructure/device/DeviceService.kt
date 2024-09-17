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
import javax.inject.Inject
import javax.net.ssl.*

class DeviceService @Inject constructor(
    private val scope: CoroutineScope,
    private val identityProvider: IdentityProvider,
    private val networkHelper: NetworkHelper,
    private val deviceRepository: DeviceRepository,
    private val networkService: NetworkService
) {

    private val _deviceHandles = MutableStateFlow<Map<String, DeviceHandle>>(emptyMap())
    val deviceHandles = _deviceHandles.asStateFlow()

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
        val port = 33587

        if (_deviceHandles.value.containsKey(id)) return

        val device = savedDevicesFlow.value[id]
        val pairStatus = if (device?.certificate != null) PairStatus.PAIRED else PairStatus.UNPAIRED

        val deviceHandle = DeviceHandle(pairStatus, name, id, null)
        addHandle(deviceHandle)
        scope.launch {
            val result = connectToDevice(ip!!, port, device)
            handleConnection(result, id, ip, port, name)
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
            updateHandle(id) {
                it.copy(name = name, certificate = sslSocket.session.peerCertificates[0] as X509Certificate).apply {
                    this.link = Link(sslSocket, this.localScope)
                }
            }
            _deviceHandles.value[id]!!.let {
                it.reloadPlugins(identityProvider)
                it.localScope.launch(Dispatchers.Unconfined) {
                    observeDevicePackets(it.link!!, id)
                }
                it.start()
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
        val handle = _deviceHandles.value[handleId] ?: return
        when {
            pair.value && handle.pairStatus == PairStatus.UNPAIRED -> {
                Timber.d("Received pair request")
                updateHandle(handleId) { it.copy(pairStatus = PairStatus.PAIR_REQUESTED_BY_PEER) }
            }
            pair.value && handle.pairStatus == PairStatus.PAIR_REQUESTED -> {
                Timber.d("Pair request accepted by peer ${handle.id}, saving")
                updateHandle(handleId) { it.copy(pairStatus = PairStatus.PAIRED) }
                deviceRepository.insert(Device(handle.id, handle.name, handle.certificate))
                handle.reloadPlugins(identityProvider)
            }
            !pair.value && handle.pairStatus == PairStatus.PAIR_REQUESTED -> {
                Timber.d("Pair request rejected")
                updateHandle(handleId) { it.copy(pairStatus = PairStatus.UNPAIRED) }
                handle.outgoingPairRequestTimerJob?.cancel()
            }
            else -> Timber.d("Pair request in unexpected state: ${handle.pairStatus}")
        }
    }

    private fun onDeviceDisconnected(id: String) {
        _deviceHandles.update {
            it[id]?.stop()
            it.filter { (key, _) -> key != id }
        }
    }

    private fun updateHandle(id: String, action: (handle: DeviceHandle) -> DeviceHandle) {
        _deviceHandles.update {
            it.toMutableMap().apply {
                this[id] = this[id]!!.let(action)
            }
        }
    }

    private fun addHandle(handle: DeviceHandle) {
        if (_deviceHandles.value.containsKey(handle.id)) return
        _deviceHandles.update {
            it.toMutableMap().apply {
                this[handle.id] = handle
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
            Platform.Android
        )

        val packet = Packet.createPacket(builder, Component.DeviceIdentityResponse, response, -1)
        builder.finishSizePrefixed(packet)

        Channels.newChannel(socket.getOutputStream()).write(builder.dataBuffer())
    }

    private fun onNetworkLost() {
        Timber.d("Network lost")
        _deviceHandles.update { map ->
            map.values.forEach { it.stop() }
            emptyMap()
        }
    }

    fun sendOutgoingPairRequest(id: String, scope: CoroutineScope? = null) {
        if (!_deviceHandles.value.containsKey(id)) return
        if (_deviceHandles.value[id]!!.pairStatus == PairStatus.PAIRED ||
            _deviceHandles.value[id]!!.pairStatus == PairStatus.PAIR_REQUESTED) {
            Timber.d("Pair request already in progress")
            return
        }

        updateHandle(id) { handle ->
            handle.copy(pairStatus = PairStatus.PAIR_REQUESTED).apply {
                this.outgoingPairRequestTimerJob = (scope ?: this.localScope).launch {
                    delay(30000)
                    Timber.d("Pair request timed out")
                    updateHandle(id) {
                        it.copy(pairStatus = PairStatus.UNPAIRED)
                    }
                }
            }
        }

        val builder = FlatBufferBuilder(256)
        val pair = Pair.createPair(builder, true)
        val packet = Packet.createPacket(builder, Component.Pair, pair, -1)
        builder.finishSizePrefixed(packet)
        _deviceHandles.value[id]!!.sendPacket(builder.dataBuffer())
    }

}

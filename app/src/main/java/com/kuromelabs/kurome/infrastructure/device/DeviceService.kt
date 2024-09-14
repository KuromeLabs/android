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

    private val deviceHandles = ConcurrentHashMap<String, DeviceHandle>()
    private val deviceStatesFlow = MutableStateFlow<Map<String, DeviceState>>(emptyMap())
    val deviceStates = deviceStatesFlow.asStateFlow()

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

        if (deviceHandles.containsKey(id)) return

        val device = savedDevicesFlow.value[id]
        val pairStatus = if (device?.certificate != null) PairStatus.PAIRED else PairStatus.UNPAIRED

        val deviceHandle = DeviceHandle(pairStatus, name, id, null)
        deviceHandles[id] = deviceHandle

        scope.launch {
            val result = connectToDevice(ip!!, port, device)
            handleConnection(result, deviceHandle, id, ip, port, name)
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
        deviceHandle: DeviceHandle,
        id: String,
        ip: String,
        port: Int,
        name: String
    ) {
        result.onSuccess { sslSocket ->
            Timber.d("Connected to $ip:$port, name: $name, id: $id")
            setupDeviceHandle(deviceHandle, sslSocket)
            deviceHandle.start()
            reloadDeviceStates()
        }.onFailure {
            onDeviceDisconnected(id)
            Timber.e("Failed to connect to $ip:$port")
        }
    }

    private fun setupDeviceHandle(deviceHandle: DeviceHandle, sslSocket: SSLSocket) {
        val link = Link(sslSocket, deviceHandle.localScope)
        deviceHandle.link = link
        deviceHandle.certificate = sslSocket.session.peerCertificates[0] as X509Certificate
        deviceHandle.reloadPlugins(identityProvider)
        deviceHandle.localScope.launch(Dispatchers.Unconfined) {
            observeDevicePackets(link, deviceHandle)
        }
    }

    private suspend fun observeDevicePackets(link: Link, deviceHandle: DeviceHandle) {
        link.receivedPackets
            .filter { it.isFailure || (it.isSuccess && it.value!!.componentType == Component.Pair) }
            .collect { packetResult ->
                packetResult.onSuccess { handlePairPacket(it.component(Kurome.Fbs.Pair()) as Pair, deviceHandle) }
                    .onFailure { onDeviceDisconnected(deviceHandle.id) }
            }
    }

    private suspend fun handlePairPacket(pair: Pair, handle: DeviceHandle) {
        when {
            pair.value && handle.pairStatus == PairStatus.UNPAIRED -> {
                Timber.d("Received pair request")
                handle.pairStatus = PairStatus.PAIR_REQUESTED_BY_PEER
            }
            pair.value && handle.pairStatus == PairStatus.PAIR_REQUESTED -> {
                Timber.d("Pair request accepted by peer ${handle.id}, saving")
                handle.pairStatus = PairStatus.PAIRED
                deviceRepository.insert(Device(handle.id, handle.name, handle.certificate))
                handle.reloadPlugins(identityProvider)
            }
            !pair.value && handle.pairStatus == PairStatus.PAIR_REQUESTED -> {
                Timber.d("Pair request rejected")
                handle.pairStatus = PairStatus.UNPAIRED
                handle.outgoingPairRequestTimerJob?.cancel()
            }
            else -> Timber.d("Pair request in unexpected state: ${handle.pairStatus}")
        }
        reloadDeviceStates()
    }

    private fun onDeviceDisconnected(id: String) {
        deviceHandles.remove(id)?.stop()
        reloadDeviceStates()
    }

    private fun reloadDeviceStates() {
        deviceStatesFlow.value = deviceHandles.mapValues { (_, handle) ->
            DeviceState(handle.name, handle.id, handle.pairStatus, true)
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
        deviceHandles.values.forEach { it.stop() }
        deviceHandles.clear()
        reloadDeviceStates()
    }

    suspend fun sendOutgoingPairRequest(id: String) {
        val deviceHandle = deviceHandles[id] ?: return
        if (deviceHandle.pairStatus == PairStatus.PAIRED || deviceHandle.pairStatus == PairStatus.PAIR_REQUESTED) {
            Timber.d("Pair request already in progress")
            return
        }

        deviceHandle.pairStatus = PairStatus.PAIR_REQUESTED
        deviceHandle.outgoingPairRequestTimerJob = scope.launch {
            delay(30000)
            Timber.d("Pair request timed out")
            deviceHandle.pairStatus = PairStatus.UNPAIRED
        }

        val builder = FlatBufferBuilder(256)
        val pair = Pair.createPair(builder, true)
        val packet = Packet.createPacket(builder, Component.Pair, pair, -1)
        builder.finishSizePrefixed(packet)
        deviceHandle.sendPacket(builder.dataBuffer())
    }


}

data class DeviceState(
    val name: String,
    val id: String,
    val pairStatus: PairStatus,
    val connected: Boolean
)

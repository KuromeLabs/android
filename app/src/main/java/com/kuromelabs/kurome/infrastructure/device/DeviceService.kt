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

    fun handleUdpPacket(packet: DeviceIdentityResponse) {
        val name = packet.name!!
        val id = packet.id!!
        val ip = packet.localIp
        val port = packet.tcpListeningPort
        Timber.d("Received UDP packet from $ip:$port, id: $id, name: $name")
        if (deviceStates.value.containsKey(id)) return

        val device = savedDevicesFlow.value[id]
        val trusted = device?.certificate != null

        val deviceHandle = DeviceHandle(trusted, "Unknown", id, null)
        addHandle(deviceHandle)
        scope.launch {
            val result = connectToDevice(ip!!, port.toInt(), device)
            handleConnection(result, id, ip, port.toInt())
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

    private suspend fun sendIdentityQuery(id: String) {
        val builder = FlatBufferBuilder(256)
        DeviceIdentityQuery.startDeviceIdentityQuery(builder)
        val query = DeviceIdentityQuery.endDeviceIdentityQuery(builder)
        val packet = Packet.createPacket(builder, Component.DeviceIdentityQuery, query, 0)
        builder.finishSizePrefixed(packet)
        _deviceHandles[id]!!.sendPacket(builder.dataBuffer())
    }

    private suspend fun handleConnection(
        result: Result<SSLSocket>,
        id: String,
        ip: String,
        port: Int
    ) {
        result.onSuccess { sslSocket ->
            updateHandle(id) {
                it.name = "Unknown"
                it.certificate = sslSocket.session.peerCertificates[0] as X509Certificate
                it.link = Link(sslSocket, it.localScope)
                it
            }
            Timber.d("Connected to $ip:$port, id: $id. Getting extended identity...")
            var identityPacket: Packet? = null
            val identityJob = _deviceHandles[id]!!.localScope.launch {
                identityPacket = _deviceHandles[id]!!.getIncomingPacketWithId(0, 35000)
            }
            _deviceHandles[id]!!.reloadPlugins(identityProvider)
            sendIdentityQuery(id)
            _deviceHandles[id]!!.link!!.start()
            identityJob.join()
            if (identityPacket == null) {
                Timber.e("Failed to get extended identity")
                onDeviceDisconnected(id)
                return@onSuccess
            }
            val identity = identityPacket!!.component(DeviceIdentityResponse()) as DeviceIdentityResponse

            updateHandle(id) {
                it.name = identity.name!!
                it.localScope.launch(Dispatchers.Unconfined) {
                    observeDevicePackets(it.link!!, id)
                }
                it.localScope.launch(Dispatchers.Unconfined) {
                    it.pairHandler.pairStatus.collect {status ->
                        Timber.d("Pair status changed to $it")
                        onPairStatusChanged(status, id)
                    }
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
            .filter {it.isFailure }
            .collect { packetResult ->
                    packetResult.onFailure { onDeviceDisconnected(handleId) }
            }
    }

    private suspend fun onPairStatusChanged(pairStatus: PairStatus, handleId: String) {
        val handle = _deviceHandles[handleId] ?: return
        updateHandle(handleId) {
            handle
        }
        when (pairStatus) {
            PairStatus.PAIRED -> {
                Timber.d("Device $handleId paired")
                deviceRepository.insert(Device(handle.id, handle.name, handle.certificate))
            }
            PairStatus.UNPAIRED -> {
                Timber.d("Device $handleId unpaired")
                deviceRepository.delete(handle.id)
            }

            PairStatus.PAIR_REQUESTED -> {}
            PairStatus.PAIR_REQUESTED_BY_PEER -> {}
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
                this[id] = DeviceState(_deviceHandles[id]!!.name, id, _deviceHandles[id]!!.pairHandler.pairStatus.value, true)
            }
        }
    }

    private fun addHandle(handle: DeviceHandle) {
        _deviceHandles[handle.id] = handle
        _deviceStates.update {
            it.toMutableMap().apply {
                this[handle.id] = DeviceState(handle.name, handle.id, handle.pairHandler.pairStatus.value, true)
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
        val handle = _deviceHandles[id]!!
        handle.pairHandler.sendOutgoingPairRequest()
    }

}

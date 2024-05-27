package com.kuromelabs.kurome.infrastructure.device

import Kurome.Fbs.Component
import Kurome.Fbs.DeviceIdentityQuery
import Kurome.Fbs.DeviceIdentityResponse
import Kurome.Fbs.Packet
import Kurome.Fbs.Platform
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.infrastructure.network.Link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.ByteBuffer
import java.security.cert.X509Certificate

class DeviceHandler(
//    var device: Device,
    private val scope: CoroutineScope,
    private var identityProvider: IdentityProvider,
    private val deviceTrusted: Boolean,
    var name: String,
    var id: String,
    var certificate: X509Certificate?,
    var link: Link?
) {
    private var outgoingPairRequestTimerJob: Job? = null
    private val _pairStatus: MutableStateFlow<PairStatus> = MutableStateFlow(if (deviceTrusted) PairStatus.PAIRED else PairStatus.UNPAIRED)
    val pairStatus: StateFlow<PairStatus> = _pairStatus.asStateFlow()
    private lateinit var fsAccessor : FilesystemPacketHandler
    private var handleJob: Job? = null

    fun onNetworkLost() {
        stop()
    }

    fun stop() {
        handleJob?.cancel()
        link?.close()
    }

    suspend fun start() {
        fsAccessor = FilesystemPacketHandler(link!!)
        val linkJob = link!!.start()
        handleJob = scope.launch {
            link!!.receivedPackets.collect {
                processPacket(it)
            }
        }
        scope.launch {
            link!!.isConnected.collect { connected ->
                Timber.d("Link connected status: $connected")
                if (!connected) {
                    coroutineContext.job.cancel()
                    stop()
                    linkJob.cancel()
                }
            }
        }
    }

    private suspend fun processPacket(packet: Packet) {
        when (packet.componentType)
        {
            Component.DeviceIdentityQuery -> {
                val builder = FlatBufferBuilder(256)
                val deviceQuery = packet.component(DeviceIdentityQuery()) as DeviceIdentityQuery
                val response = processDeviceQuery(builder)

                val p = Packet.createPacket(
                    builder,
                    Component.DeviceIdentityResponse,
                    response,
                    packet.id
                )
                builder.finishSizePrefixed(p)
                val buffer = builder.dataBuffer()
                sendPacket(buffer)
            }
            Component.Pair -> {
                handleIncomingPairPacket(packet)
            }
            else -> {
                if (_pairStatus.value == PairStatus.PAIRED)
                    fsAccessor.processFileAction(packet)
            }

        }
    }

    private fun processDeviceQuery(builder: FlatBufferBuilder): Int {
        val name = Build.MODEL
        val statFs = StatFs(Environment.getDataDirectory().path)
        return DeviceIdentityResponse.createDeviceIdentityResponse(
            builder,
            statFs.totalBytes,
            statFs.freeBytes,
            builder.createString(name),
            builder.createString(identityProvider.getEnvironmentId()),
            builder.createString(""),
            Platform.Android
        )

    }

    private fun sendPacket(packet: ByteBuffer) {
        link!!.send(packet)
    }

    private suspend fun handleIncomingPairPacket(packet: Packet) {
        val pair = packet.component(Kurome.Fbs.Pair()) as Kurome.Fbs.Pair
        if (pair.value) {
            when (_pairStatus.value) {
                PairStatus.PAIRED -> {
                    //TODO: handle. maybe unpair first?
                    Timber.d("Received incoming pair request, but already paired")
                }

                PairStatus.UNPAIRED -> {
                    //Incoming pair request. TODO: Implement UI to accept or reject
                    Timber.d("Received incoming pair request")
                    _pairStatus.value = PairStatus.PAIR_REQUESTED_BY_PEER
                }

                PairStatus.PAIR_REQUESTED -> {
                    //We made outgoing pair request and it was accepted
                    Timber.d("Outgoing pair request accepted by peer ${id}, saving")
                    _pairStatus.value = PairStatus.PAIRED
                    outgoingPairRequestTimerJob?.cancel()
                }

                else -> {
                    Timber.d("Received pair request, but status is ${pairStatus}")
                }
            }
        } else {
            when (_pairStatus.value) {
                PairStatus.PAIR_REQUESTED -> {
                    //We made outgoing pair request and it was rejected
                    _pairStatus.value = PairStatus.UNPAIRED
                    outgoingPairRequestTimerJob?.cancel()
                }

                PairStatus.PAIRED -> {
                    //Incoming unpair request. TODO: implement unpair
                }

                else -> {
                    Timber.d("Received unpair request, but status is ${pairStatus}")
                }
            }
        }
    }

    suspend fun sendOutgoingPairRequest() {
        if (!link!!.isConnected.first()) return
        // send pair request with 30s timeout
        if (_pairStatus.value == PairStatus.PAIRED) {
            Timber.d("Already paired")
            return
        }
        if (_pairStatus.value == PairStatus.PAIR_REQUESTED) {
            Timber.d("Pair request already sent")
            return
        }
        _pairStatus.value = PairStatus.PAIR_REQUESTED


        outgoingPairRequestTimerJob = scope.launch {
            delay(30000)
            Timber.d("Pair request timed out")
            _pairStatus.value = PairStatus.UNPAIRED
            coroutineContext.job.cancel()
        }
        val builder = FlatBufferBuilder(256)
        val pair = Kurome.Fbs.Pair.createPair(builder, true)
        val p = Packet.createPacket(builder, Component.Pair, pair, -1)
        builder.finishSizePrefixed(p)
        val buffer = builder.dataBuffer()
        link!!.send(buffer)
    }
}

enum class PairStatus {
    PAIRED, UNPAIRED, PAIR_REQUESTED, PAIR_REQUESTED_BY_PEER
}
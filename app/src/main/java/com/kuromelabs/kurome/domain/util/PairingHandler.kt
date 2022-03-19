package com.kuromelabs.kurome.domain.util

import com.google.flatbuffers.FlatBufferBuilder
import com.kuromelabs.kurome.domain.model.Device
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kurome.Action
import kurome.DeviceInfo
import kurome.Packet
import kurome.PairEvent
import timber.log.Timber

@OptIn(ExperimentalUnsignedTypes::class)
class PairingHandler(val device: Device) {

    enum class PairStatus {
        NotPaired, Requested, RequestedByRemote, Paired
    }

    enum class PairingType {
        IncomingRequest, PairingDone, PairingFailed, Unpaired
    }

    private val _pairingHandlerFlow = MutableSharedFlow<PairingType>(0)
    val pairingHandlerFlow = _pairingHandlerFlow
    var status: PairStatus? = null
    var pairRequestTimerJob: Job? = null



    suspend fun packetReceived(packet: Packet){
        Timber.d("Received pair packet")
        val wantsPair = packet.pair == PairEvent.pair
        val isPaired = status == PairStatus.Paired

        if (!wantsPair && !isPaired) {
            Timber.d("Pairing request rejected by remote")
            status = PairStatus.NotPaired
            pairRequestTimerJob?.cancel()
        }

        if (wantsPair){
            if (status == PairStatus.Requested) {
                Timber.d("Pairing accepted by remote")
                device.isPaired = true
                _pairingHandlerFlow.emit(PairingType.PairingDone)
                pairRequestTimerJob?.cancel()
            }
        }
    }

    suspend fun requestPairing() {
        if (status != PairStatus.Requested) { //if not already requested
            Timber.d("Requesting pairing for ${device.name}")
            status = PairStatus.Requested
            val builder = FlatBufferBuilder(1024)
            val idOff = builder.createString(device.id)
            val nameOff = builder.createString(device.name)
            val deviceInfo = DeviceInfo.createDeviceInfo(builder, nameOff, idOff, 0, 0, 0)

            val packet =
                Packet.createPacket(builder, 0, Action.actionPair, 0, deviceInfo, 0, 0, idOff, PairEvent.pair)
            builder.finishSizePrefixed(packet)
            device.sendBuffer(builder.dataBuffer())
            pairRequestTimerJob = CoroutineScope(Dispatchers.Default).launch {
                delay(30000)
                if (currentCoroutineContext().isActive) {
                    Timber.d("Pairing request timed out")
                    status = PairStatus.NotPaired
                    _pairingHandlerFlow.emit(PairingType.PairingFailed)
                }
            }
        }
    }
}
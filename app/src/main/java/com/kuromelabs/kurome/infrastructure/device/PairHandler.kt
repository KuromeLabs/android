package com.kuromelabs.kurome.infrastructure.device

import Kurome.Fbs.Component
import Kurome.Fbs.Packet
import Kurome.Fbs.Pair
import com.google.flatbuffers.FlatBufferBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import timber.log.Timber

class PairHandler(val handle: DeviceHandle, val initialPairStatus: PairStatus) {
    var outgoingPairRequestTimerJob: Job? = null
    private val _pairStatus = MutableStateFlow<PairStatus>(initialPairStatus)
    val pairStatus = _pairStatus.asStateFlow()
    var isStarted = false
    fun start() {
        isStarted = true
        handle.localScope.launch {
            handle.link?.receivedPackets?.filter {
                it.isSuccess && it.value!!.componentType == Component.Pair
            }!!.collect {
                it.onSuccess { handlePairPacket(it.component(Pair()) as Pair) }
            }
        }
    }

    private fun handlePairPacket(pair: Pair) {
        when {
            pair.value && pairStatus.value == PairStatus.UNPAIRED -> {
                _pairStatus.value = PairStatus.PAIR_REQUESTED_BY_PEER
                Timber.d("Received pair request")
            }
            pair.value && pairStatus.value == PairStatus.PAIR_REQUESTED -> {
                _pairStatus.value = PairStatus.PAIRED
                outgoingPairRequestTimerJob?.cancel()
                Timber.d("Pair request accepted by peer ${handle.id}, saving")
            }
            !pair.value && pairStatus.value == PairStatus.PAIR_REQUESTED -> {
                _pairStatus.value = PairStatus.UNPAIRED
                Timber.d("Pair request rejected")
                outgoingPairRequestTimerJob?.cancel()
            }
            else -> Timber.d("Pair request in unexpected state: ${pairStatus.value}")
        }
    }

    fun sendOutgoingPairRequest() {
        if (pairStatus.value == PairStatus.PAIRED || pairStatus.value == PairStatus.PAIR_REQUESTED) {
            Timber.d("Pair request already in progress")
            return
        }
        _pairStatus.value = PairStatus.PAIR_REQUESTED
        outgoingPairRequestTimerJob = (handle.localScope).launch {
            delay(30000)
            Timber.d("Pair request timed out")
            _pairStatus.value = PairStatus.UNPAIRED
            outgoingPairRequestTimerJob = null
        }


        val builder = FlatBufferBuilder(256)
        val pair = Pair.createPair(builder, true)
        val packet = Packet.createPacket(builder, Component.Pair, pair, -126)
        builder.finishSizePrefixed(packet)
        handle.sendPacket(builder.dataBuffer())
    }
}
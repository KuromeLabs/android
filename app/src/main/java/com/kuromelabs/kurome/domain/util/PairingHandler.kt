package com.kuromelabs.kurome.domain.util

import com.kuromelabs.kurome.domain.model.Device
import kotlinx.coroutines.flow.MutableSharedFlow
import kurome.Packet


class PairingHandler(val device: Device? = null) {

    enum class PairStatus {
        NotPaired, Requested, RequestedByRemote, Paired
    }

    enum class PairingType {
        IncomingRequest, PairingDone, PairingFailed, Unpaired
    }

    private val _pairingHandlerFlow = MutableSharedFlow<PairingType>(0)
    val pairingHandlerFlow = _pairingHandlerFlow

    fun packetReceived(packet: Packet){

    }

}
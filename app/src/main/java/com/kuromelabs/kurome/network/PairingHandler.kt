package com.kuromelabs.kurome.network

import com.kuromelabs.kurome.models.Device
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
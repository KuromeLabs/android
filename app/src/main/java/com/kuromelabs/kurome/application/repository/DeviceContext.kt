package com.kuromelabs.kurome.application.repository

import com.kuromelabs.kurome.domain.Device

class DeviceContext (
    var device: Device,
    var state: State
) {
    enum class State {
        PAIR_REQUESTED,
        PAIR_REQUESTED_BY_PEER,
        CONNECTING,
        CONNECTED_TRUSTED,
        CONNECTED_UNTRUSTED,
        DISCONNECTED
    }

    fun isConnectedOrConnecting(): Boolean {
        return state == State.CONNECTED_TRUSTED || state == State.CONNECTED_UNTRUSTED || state == State.CONNECTING
    }

}



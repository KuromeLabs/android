package com.kuromelabs.kurome.domain.use_case

import com.kuromelabs.kurome.domain.model.Device
import com.kuromelabs.kurome.domain.repository.DeviceRepository
import com.kuromelabs.kurome.domain.util.PairingHandler
import kotlinx.coroutines.flow.collectLatest


class PairDevice(private val repository: DeviceRepository) {

    suspend operator fun invoke(device: Device) {
        if (!device.isPaired && device.isConnected()) {
            val pairFlow = device.requestPairing()
            pairFlow.collectLatest {
                when (it) {
                    PairingHandler.PairingType.PairingDone -> {
                        repository.insert(device)
                    }
                }
            }
        }
    }
}


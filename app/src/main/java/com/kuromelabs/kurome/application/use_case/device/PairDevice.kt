package com.kuromelabs.kurome.application.use_case.device

import com.kuromelabs.kurome.infrastructure.device.DeviceService


class PairDevice(private val deviceService: DeviceService) {

    suspend operator fun invoke(id: String) {
        deviceService.sendOutgoingPairRequest(id)
    }
}


package com.kuromelabs.kurome.application.use_case.device

import com.kuromelabs.kurome.application.devices.Device
import com.kuromelabs.kurome.application.devices.DeviceRepository
import com.kuromelabs.kurome.infrastructure.device.DeviceService


class PairDevice(private val deviceService: DeviceService) {

    operator fun invoke(device: Device) {
        deviceService.sendOutgoingPairRequest(device.id)
    }
}


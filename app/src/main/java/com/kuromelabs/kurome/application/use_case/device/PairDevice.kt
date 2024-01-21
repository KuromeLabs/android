package com.kuromelabs.kurome.application.use_case.device

import com.kuromelabs.kurome.application.devices.Device
import com.kuromelabs.kurome.application.devices.DeviceRepository


class PairDevice(private val repository: DeviceRepository) {

    suspend operator fun invoke(device: Device) {
        //TODO: Implement
    }
}


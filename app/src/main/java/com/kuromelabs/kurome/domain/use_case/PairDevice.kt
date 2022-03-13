package com.kuromelabs.kurome.domain.use_case

import com.kuromelabs.kurome.domain.model.Device
import com.kuromelabs.kurome.domain.model.PairingException
import com.kuromelabs.kurome.domain.repository.DeviceRepository


class PairDevice(private val repository: DeviceRepository) {

    @Throws(PairingException::class)
    suspend operator fun invoke(device: Device) {
        //TODO: Implement
    }
}


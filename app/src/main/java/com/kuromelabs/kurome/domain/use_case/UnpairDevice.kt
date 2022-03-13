package com.kuromelabs.kurome.domain.use_case

import com.kuromelabs.kurome.domain.model.Device
import com.kuromelabs.kurome.domain.repository.DeviceRepository

class UnpairDevice(private val repository: DeviceRepository) {
    suspend operator fun invoke(device: Device){
        //TODO: Implement
    }
}
package com.kuromelabs.kurome.domain.use_case

import com.kuromelabs.kurome.domain.model.Device
import com.kuromelabs.kurome.domain.repository.DeviceRepository

class GetDevice(private val repository: DeviceRepository) {
    suspend operator fun invoke(id: String): Device? {
        return repository.getDevice(id)
    }
}
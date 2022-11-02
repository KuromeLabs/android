package com.kuromelabs.kurome.application.use_case.device

import com.kuromelabs.kurome.application.interfaces.DeviceRepository
import com.kuromelabs.kurome.domain.Device

class GetSavedDevice(private val repository: DeviceRepository) {
    suspend operator fun invoke(id: String): Device? {
        return repository.getSavedDevice(id)
    }
}
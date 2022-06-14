package com.kuromelabs.kurome.application.use_case.device

import com.kuromelabs.kurome.application.interfaces.DeviceRepository
import com.kuromelabs.kurome.domain.Device
import kotlinx.coroutines.flow.Flow

class GetDevices(private val repository: DeviceRepository) {
    operator fun invoke(): Flow<List<Device>> {
        return repository.getCombinedDevices()
    }
}
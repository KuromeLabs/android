package com.kuromelabs.kurome.domain.use_case

import com.kuromelabs.kurome.domain.model.Device
import com.kuromelabs.kurome.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow

class GetDevices(private val repository: DeviceRepository) {
    operator fun invoke(): Flow<List<Device>> {
        return repository.getCombinedDevices()
    }
}
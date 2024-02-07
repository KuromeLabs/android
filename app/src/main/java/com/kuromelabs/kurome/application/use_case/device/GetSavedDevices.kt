package com.kuromelabs.kurome.application.use_case.device

import com.kuromelabs.kurome.application.devices.Device
import com.kuromelabs.kurome.application.devices.DeviceRepository
import kotlinx.coroutines.flow.Flow

class GetSavedDevices(private val repository: DeviceRepository) {
    operator fun invoke(): Flow<List<Device>> {
        return repository.getSavedDevices()
    }
}
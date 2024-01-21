package com.kuromelabs.kurome.application.use_case.device

import com.kuromelabs.kurome.application.devices.DeviceRepository
import com.kuromelabs.kurome.infrastructure.device.DeviceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetSavedDevices(private val repository: DeviceRepository) {
    operator fun invoke(): Flow<List<DeviceState>> {
        return repository.getSavedDevices().map { devices ->
            devices.map { DeviceState(it, DeviceState.Status.DISCONNECTED) }
        }
    }
}
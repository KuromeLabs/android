package com.kuromelabs.kurome.application.use_case.device

import com.kuromelabs.kurome.application.repository.DeviceContext
import com.kuromelabs.kurome.application.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetSavedDevices(private val repository: DeviceRepository) {
    operator fun invoke(): Flow<List<DeviceContext>> {
        return repository.getSavedDevices().map { devices ->
            devices.map { DeviceContext(it, DeviceContext.State.DISCONNECTED) }
        }
    }
}
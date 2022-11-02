package com.kuromelabs.kurome.application.use_case.device

import com.kuromelabs.kurome.application.interfaces.DeviceAccessor
import com.kuromelabs.kurome.application.interfaces.DeviceRepository
import kotlinx.coroutines.flow.Flow

class GetConnectedDevices(val repository: DeviceRepository) {
    operator fun invoke(): Flow<List<DeviceAccessor>> {
        return repository.getDeviceAccessors()
    }
}
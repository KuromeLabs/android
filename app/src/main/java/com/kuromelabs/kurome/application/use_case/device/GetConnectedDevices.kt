package com.kuromelabs.kurome.application.use_case.device

import com.kuromelabs.kurome.application.interfaces.DeviceRepository
import com.kuromelabs.kurome.domain.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

class GetConnectedDevices(val repository: DeviceRepository) {
    operator fun invoke(): Flow<List<Device>> {
        return repository.getActiveDevices().transform { devices -> emit(devices.values.toList()) }
    }
}
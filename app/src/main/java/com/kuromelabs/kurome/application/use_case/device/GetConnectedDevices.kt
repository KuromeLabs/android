package com.kuromelabs.kurome.application.use_case.device

import com.kuromelabs.kurome.application.repository.DeviceContext
import com.kuromelabs.kurome.application.repository.DeviceRepository
import com.kuromelabs.kurome.domain.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

class GetConnectedDevices(val repository: DeviceRepository) {
    operator fun invoke(): Flow<List<DeviceContext>> {
        return repository.getDeviceContexts().transform { deviceContexts ->
            emit(deviceContexts.values.filter { it.isConnectedOrConnecting() })
        }
    }
}
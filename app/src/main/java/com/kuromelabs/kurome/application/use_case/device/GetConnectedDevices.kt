package com.kuromelabs.kurome.application.use_case.device

import com.kuromelabs.kurome.infrastructure.device.DeviceService
import com.kuromelabs.kurome.infrastructure.device.DeviceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

class GetConnectedDevices(val deviceService: DeviceService) {
    operator fun invoke(): Flow<List<DeviceState>> {
        return deviceService.deviceStates.transform { deviceStates ->
            emit(deviceStates.values.filter { it.isConnectedOrConnecting() })
        }
    }
}
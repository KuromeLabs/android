package com.kuromelabs.kurome.application.use_case.device

import com.kuromelabs.kurome.infrastructure.device.DeviceService

class ManuallyConnectDevice(private val deviceService: DeviceService) {
    operator fun invoke(ip: String) {
        deviceService.handleUdp("ManuallyConnectDevice", "ManuallyConnectedDeviceId", ip, 33587)
    }
}
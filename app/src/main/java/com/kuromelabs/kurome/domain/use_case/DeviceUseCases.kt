package com.kuromelabs.kurome.domain.use_case

data class DeviceUseCases (
    val getDevices: GetDevices,
    val pairDevice: PairDevice,
    val unpairDevice: UnpairDevice,
    val getDevice: GetDevice
)
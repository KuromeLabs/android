package com.kuromelabs.kurome.application.use_case.device

data class DeviceUseCases(
    val getDevices: GetDevices,
    val pairDevice: PairDevice,
    val unpairDevice: UnpairDevice,
    val getDevice: GetDevice,
    val connect: Connect,
    val monitor: Monitor
)
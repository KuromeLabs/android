package com.kuromelabs.kurome.application.use_case.device

data class DeviceUseCases(
    val getSavedDevices: GetSavedDevices,
    val getConnectedDevices: GetConnectedDevices,
    val pairDevice: PairDevice,
    val unpairDevice: UnpairDevice,
    val getSavedDevice: GetSavedDevice,
    val manuallyConnectDevice: ManuallyConnectDevice
)
package com.kuromelabs.kurome.presentation.ui.devices

import com.kuromelabs.kurome.application.devices.Device

sealed class DevicesEvent {
    data class PairDevice(val device: Device) : DevicesEvent()
    data class UnpairDevice(val device: Device) : DevicesEvent()
    data object GetDevices : DevicesEvent()
}

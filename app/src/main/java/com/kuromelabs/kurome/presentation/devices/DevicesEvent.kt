package com.kuromelabs.kurome.presentation.devices

import com.kuromelabs.kurome.domain.model.Device

sealed class DevicesEvent{
    data class PairDevice(val device: Device) : DevicesEvent()
    data class UnpairDevice(val device: Device) : DevicesEvent()
    object GetDevices : DevicesEvent()
}

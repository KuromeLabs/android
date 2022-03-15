package com.kuromelabs.kurome.presentation.devices

import com.kuromelabs.kurome.domain.model.Device

data class DevicesState(
    val devices: List<DeviceState> = emptyList(),
    val isLoading: Boolean = false,
    val error: Throwable? = null
)

data class DeviceState(
    val device: Device,
    val isConnected: Boolean,
    val isPaired: Boolean
)
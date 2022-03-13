package com.kuromelabs.kurome.presentation.devices

import com.kuromelabs.kurome.domain.model.Device

data class DevicesState(
    val devices: List<Device> = emptyList(),
    val isLoading: Boolean = false,
    val error: Throwable? = null
)
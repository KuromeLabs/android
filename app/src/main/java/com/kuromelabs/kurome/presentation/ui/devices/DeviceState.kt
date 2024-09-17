package com.kuromelabs.kurome.presentation.ui.devices

import com.kuromelabs.kurome.infrastructure.device.PairStatus

data class DeviceState(
    val name: String,
    val id: String,
    val pairStatus: PairStatus,
    val connected: Boolean
)
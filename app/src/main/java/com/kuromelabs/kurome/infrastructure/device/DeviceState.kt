package com.kuromelabs.kurome.infrastructure.device

data class DeviceState(
    val name: String,
    val id: String,
    val pairStatus: PairStatus,
    val connected: Boolean
)
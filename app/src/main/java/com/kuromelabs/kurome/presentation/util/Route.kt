package com.kuromelabs.kurome.presentation.util

import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable
    data object Permissions : Route

    @Serializable
    data object Devices : Route

    @Serializable
    data class DeviceDetail(val deviceId: String, val deviceName: String) : Route

    @Serializable
    data object AddDevice : Route
}

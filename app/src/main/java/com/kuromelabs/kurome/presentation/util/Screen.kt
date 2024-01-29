package com.kuromelabs.kurome.presentation.util

sealed class Screen(val route: String) {
    data object DevicesScreen : Screen("devices_screen")
    data object PermissionsScreen : Screen("permissions_screen")
    data object DeviceDetailScreen : Screen("device_detail_screen")
    data object AddDeviceScreen : Screen("add_device_screen")
}

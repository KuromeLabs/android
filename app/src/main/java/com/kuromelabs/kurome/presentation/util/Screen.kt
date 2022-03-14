package com.kuromelabs.kurome.presentation.util

sealed class Screen(val route: String) {
    object DevicesScreen: Screen("devices_screen")
    object PermissionsScreen: Screen("permissions_screen")
}

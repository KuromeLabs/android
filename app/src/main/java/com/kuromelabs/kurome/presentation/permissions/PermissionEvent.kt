package com.kuromelabs.kurome.presentation.permissions

sealed class PermissionEvent {
    data class Granted(val permission: String) : PermissionEvent()
    data class Revoked(val permission: String) : PermissionEvent()
}
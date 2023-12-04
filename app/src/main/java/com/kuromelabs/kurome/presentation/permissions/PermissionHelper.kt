package com.kuromelabs.kurome.presentation.permissions


enum class PermissionStatus(val value: Int) {
    Unset(-1),
    Denied(0),
    Granted(1),
    DeniedForever(2);

    companion object {
        private val map = entries.associateBy(PermissionStatus::value)
        fun fromInt(type: Int) = map[type]
    }
}
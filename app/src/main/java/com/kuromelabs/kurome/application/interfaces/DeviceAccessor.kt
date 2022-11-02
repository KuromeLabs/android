package com.kuromelabs.kurome.application.interfaces

import com.kuromelabs.kurome.domain.Device


interface DeviceAccessor {
    fun start()

    fun get(): Device
}
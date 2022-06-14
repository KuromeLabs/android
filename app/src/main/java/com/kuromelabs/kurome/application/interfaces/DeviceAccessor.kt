package com.kuromelabs.kurome.application.interfaces

import kotlinx.coroutines.CoroutineScope

interface DeviceAccessor {
    fun start(scope: CoroutineScope)
}
package com.kuromelabs.kurome.application.interfaces

import com.kuromelabs.kurome.domain.Device
import com.kuromelabs.kurome.infrastructure.device.DeviceAccessorImpl
import dagger.assisted.AssistedFactory


interface DeviceAccessorFactory {
    fun create(link: Link, device: Device): DeviceAccessor
}

@AssistedFactory
interface DeviceAccessorImplFactory : DeviceAccessorFactory {
    override fun create(link: Link, device: Device): DeviceAccessorImpl
}
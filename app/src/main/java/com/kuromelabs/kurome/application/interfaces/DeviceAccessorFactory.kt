package com.kuromelabs.kurome.application.interfaces

import com.kuromelabs.kurome.domain.Device

interface DeviceAccessorFactory {
    fun register(id: String, accessor: DeviceAccessor)
    fun unregister(id: String)
    fun get(id: String): DeviceAccessor?
    fun create(link: Link, device: Device): DeviceAccessor
}
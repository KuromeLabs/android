package com.kuromelabs.kurome.application.interfaces

import com.kuromelabs.kurome.domain.Device
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {

    fun getSavedDevices(): Flow<List<Device>>

    suspend fun getSavedDevice(id: String): Device?

    suspend fun insert(device: Device)

    fun getDeviceAccessors(): Flow<List<DeviceAccessor>>

    fun addDeviceAccessor(id: String, accessor: DeviceAccessor)

    suspend fun removeDeviceAccessor(id: String)
}
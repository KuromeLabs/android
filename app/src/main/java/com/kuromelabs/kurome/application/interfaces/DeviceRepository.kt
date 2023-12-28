package com.kuromelabs.kurome.application.interfaces

import com.kuromelabs.kurome.domain.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DeviceRepository {

    fun getSavedDevices(): Flow<List<Device>>

    suspend fun getSavedDevice(id: String): Device?

    suspend fun insert(device: Device)

    fun getActiveDevices(): StateFlow<HashMap<String, Device>>

    fun addActiveDevice(device: Device)

    fun removeActiveDevice(device: Device)
}
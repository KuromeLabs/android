package com.kuromelabs.kurome.domain.repository

import com.kuromelabs.kurome.domain.model.Device
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {

    fun getSavedDevices(): Flow<List<Device>>

    fun getCombinedDevices(): Flow<List<Device>>

    suspend fun getDevice(id: String): Device?

    suspend fun insert(device: Device)

    suspend fun setServiceDevices(devices: List<Device>)
}
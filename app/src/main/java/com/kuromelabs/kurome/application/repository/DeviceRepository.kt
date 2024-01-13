package com.kuromelabs.kurome.application.repository

import com.kuromelabs.kurome.domain.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DeviceRepository {

    fun getSavedDevices(): Flow<List<Device>>

    suspend fun getSavedDevice(id: String): Device?

    suspend fun insert(device: Device)

    fun getDeviceContexts(): StateFlow<HashMap<String, DeviceContext>>

    fun setDeviceState(device: Device, state: DeviceContext.State)
}
package com.kuromelabs.kurome.application.devices

import kotlinx.coroutines.flow.Flow

interface DeviceRepository {

    fun getSavedDevices(): Flow<List<Device>>

    suspend fun getSavedDevice(id: String): Device?

    suspend fun insert(device: Device)

    suspend fun delete(id: String)

//    fun getDeviceContexts(): StateFlow<HashMap<String, DeviceContext>>
//
//    fun setDeviceState(device: Device, state: DeviceContext.State)
}
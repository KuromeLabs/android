package com.kuromelabs.kurome.database

import androidx.annotation.WorkerThread
import com.kuromelabs.kurome.models.Device
import kotlinx.coroutines.flow.Flow


class DeviceRepository(private val deviceDao: DeviceDao) {
    val allDevices: Flow<List<Device>> = deviceDao.getAllDevices()

    @WorkerThread
    suspend fun insert(device: Device) {
        deviceDao.insert(device)
    }

    @WorkerThread
    suspend fun setPaired(device: Device, isPaired: Boolean){
        deviceDao.setPaired(device.id, isPaired)
    }

    @WorkerThread
    suspend fun setConnected(device: Device, isConnected: Boolean){
        deviceDao.setConnected(device.id, isConnected)
    }
}
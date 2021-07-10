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
}
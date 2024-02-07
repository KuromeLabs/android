package com.kuromelabs.kurome.infrastructure.device

import androidx.annotation.WorkerThread
import com.kuromelabs.kurome.application.devices.Device
import com.kuromelabs.kurome.application.devices.DeviceDao
import com.kuromelabs.kurome.application.devices.DeviceRepository
import kotlinx.coroutines.flow.Flow


class DefaultDeviceRepository(private val deviceDao: DeviceDao) : DeviceRepository {

    override fun getSavedDevices(): Flow<List<Device>> {
        return deviceDao.getAllDevicesAsFlow()
    }

    override suspend fun getSavedDevice(id: String): Device? {
        return deviceDao.getDevice(id)
    }

    @WorkerThread
    override suspend fun insert(device: Device) {
        deviceDao.insert(device)
    }

    override suspend fun delete(id: String) {
        deviceDao.delete(id)
    }
}
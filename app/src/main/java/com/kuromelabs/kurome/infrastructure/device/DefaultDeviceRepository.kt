package com.kuromelabs.kurome.infrastructure.device

import androidx.annotation.WorkerThread
import com.kuromelabs.kurome.application.devices.Device
import com.kuromelabs.kurome.application.devices.DeviceDao
import com.kuromelabs.kurome.application.devices.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext


class DefaultDeviceRepository(private val deviceDao: DeviceDao) : DeviceRepository {

    override fun getSavedDevices(): Flow<List<Device>> {
        return deviceDao.getAllDevicesAsFlow()
    }

    override suspend fun getSavedDevice(id: String): Device? = withContext(Dispatchers.IO) {
        deviceDao.getDevice(id)
    }

    @WorkerThread
    override suspend fun insert(device: Device) = withContext(Dispatchers.IO) {
        deviceDao.insert(device)
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        deviceDao.delete(id)
    }
}
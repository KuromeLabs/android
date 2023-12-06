package com.kuromelabs.kurome.infrastructure.repository

import androidx.annotation.WorkerThread
import com.kuromelabs.kurome.application.data_source.DeviceDao
import com.kuromelabs.kurome.application.interfaces.DeviceRepository
import com.kuromelabs.kurome.domain.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update


class DeviceRepositoryImpl(private val deviceDao: DeviceDao) : DeviceRepository {

    val activeDevices = MutableStateFlow(HashMap<String, Device>())

    override fun getSavedDevices(): Flow<List<Device>> {
        return deviceDao.getAllDevices()
    }

    override suspend fun getSavedDevice(id: String): Device? {
        return deviceDao.getDevice(id)
    }

    @WorkerThread
    override suspend fun insert(device: Device) {
        deviceDao.insert(device)
    }

    override fun getActiveDevices(): Flow<List<Device>> {
        return activeDevices.transform{ emit(it.values.toList()) }
    }

    override fun addActiveDevice(device: Device) {
        activeDevices.update { it -> HashMap(it).also { it[device.id] = device } }
    }

    override fun removeActiveDevice(device: Device) {
        activeDevices.update { it -> HashMap(it).also { it.remove(device.id) } }
    }
}
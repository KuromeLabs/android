package com.kuromelabs.kurome.infrastructure.repository

import androidx.annotation.WorkerThread
import com.kuromelabs.kurome.application.data_source.DeviceDao
import com.kuromelabs.kurome.application.interfaces.DeviceAccessor
import com.kuromelabs.kurome.application.interfaces.DeviceRepository
import com.kuromelabs.kurome.domain.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update


class DeviceRepositoryImpl(private val deviceDao: DeviceDao) : DeviceRepository {
    private val accessors: MutableStateFlow<HashMap<String, DeviceAccessor>> = MutableStateFlow(HashMap())

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

    override fun getDeviceAccessors(): Flow<List<DeviceAccessor>> {
        return accessors.transform{ emit(it.values.toList()) }
    }

    override fun addDeviceAccessor(id: String, accessor: DeviceAccessor) {
        accessors.update { x ->
            HashMap<String, DeviceAccessor>(x).also { it[id] = accessor }
        }
    }

    override suspend fun removeDeviceAccessor(id: String) {
        accessors.update { x ->
            HashMap<String, DeviceAccessor>(x).also { it.remove(id) }
        }
    }
}
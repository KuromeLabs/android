package com.kuromelabs.kurome.data.database

import androidx.annotation.WorkerThread
import com.kuromelabs.kurome.data.data_source.DeviceDao
import com.kuromelabs.kurome.domain.model.Device
import com.kuromelabs.kurome.domain.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch


class DeviceRepositoryImpl(private val deviceDao: DeviceDao): DeviceRepository {
//    val savedDevices: Flow<List<Device>> = deviceDao.getAllDevices()
    private val _serviceDevices: MutableSharedFlow<List<Device>> = MutableSharedFlow(1)

    init {
        CoroutineScope(Dispatchers.Default).launch { _serviceDevices.emit(emptyList()) }
    }

    override fun getSavedDevices(): Flow<List<Device>> {
        return deviceDao.getAllDevices()
    }

    override fun getCombinedDevices(): Flow<List<Device>> {
        return deviceDao.getAllDevices().combine(_serviceDevices) { savedDevices, serviceDevices ->
            (serviceDevices + savedDevices).distinctBy(Device::id)
        }
    }

    @WorkerThread
    override suspend fun insert(device: Device) {
        deviceDao.insert(device)
    }

    override suspend fun getDevice(id: String): Device? {
        return deviceDao.getDevice(id)
    }

    override suspend fun setServiceDevices(devices: List<Device>) {
        _serviceDevices.emit(devices)
    }
}
package com.kuromelabs.kurome.application.repository

import androidx.annotation.WorkerThread
import com.kuromelabs.kurome.BuildConfig
import com.kuromelabs.kurome.application.data_source.DeviceDao
import com.kuromelabs.kurome.domain.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber


class DeviceRepositoryImpl(private val deviceDao: DeviceDao) : DeviceRepository {
    private val deviceContexts = MutableStateFlow(HashMap<String, DeviceContext>())
    init {
        if (BuildConfig.DEBUG) {
            val device = Device("test-id", "testDevice")
            setDeviceState(device, DeviceContext.State.CONNECTED_TRUSTED)
        }
    }

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

    override fun getDeviceContexts(): StateFlow<HashMap<String, DeviceContext>> {
        return deviceContexts
    }

    override fun setDeviceState(device: Device, state: DeviceContext.State) {
        deviceContexts.update { it ->
            HashMap(it).also {
                it[device.id] = DeviceContext(device, state)
            }
        }
    }
}
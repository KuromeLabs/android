package com.kuromelabs.kurome.database

import androidx.annotation.WorkerThread
import com.kuromelabs.kurome.models.Device
import kotlinx.coroutines.flow.*
import timber.log.Timber


class DeviceRepository(private val deviceDao: DeviceDao) {
    val savedDevices: Flow<List<Device>> = deviceDao.getAllDevices()
    private val connectedDevices: MutableSharedFlow<List<Device>> = MutableSharedFlow(1)
    private val _connectedDevices: SharedFlow<List<Device>> = connectedDevices.onSubscription {
        this.emit(emptyList())
    }

    @WorkerThread
    suspend fun insert(device: Device) {
        deviceDao.insert(device)
    }

    fun get(id: String): Device? {
        return deviceDao.getDevice(id)
    }



    fun combineDevices(): Flow<List<Device>> =
        combine(savedDevices, _connectedDevices) { saved, connected ->
            val set = HashSet<Device>()
            connected.forEach {
                Timber.d("flowing connected device: $it")
                it.isConnected = true
                set.add(it)
            }
            saved.forEach { set.add(it) }
            Timber.d("emitting combined devices $set")
            ArrayList(set)
        }

    suspend fun setConnectedDevices(list: List<Device>) {
        Timber.d("emitting connected devices $list")
        connectedDevices.emit(list)
    }
}
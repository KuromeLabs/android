package com.kuromelabs.kurome.database

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.WorkerThread
import androidx.lifecycle.lifecycleScope

import com.kuromelabs.kurome.models.Device
import com.kuromelabs.kurome.services.KuromeService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber


class DeviceRepository(private val deviceDao: DeviceDao, private val context: Context) {
    val savedDevices: Flow<List<Device>> = deviceDao.getAllDevices()
    private val linkFlow = MutableStateFlow<String?>(null)
    lateinit var service: KuromeService

    init {
        bindService()
    }

    @WorkerThread
    suspend fun insert(device: Device) {
        deviceDao.insert(device)
    }

    fun get(id: String): Device? {
        return deviceDao.getDevice(id)
    }

    fun combineDevices(): Flow<List<Device>> = combine(savedDevices, linkFlow) { saved, connected ->
        val map = HashMap<String, Device>()
        saved.forEach { map[it.id] = it }
        if (connected != null) {
            Timber.d(connected)
            map[connected.drop(1)]?.isConnected = connected[0] == '.'
        }
        ArrayList(map.values)

    }

    private fun bindService() {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as KuromeService.LocalBinder).getService()
                Timber.d("Connected to service")
                service.lifecycleScope.launch {
                    service.linkFlow.collect {
                        linkFlow.emit(it)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Timber.d("Disconnected from service")
            }
        }
        Intent(context, KuromeService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }
}
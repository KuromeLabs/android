package com.kuromelabs.kurome.database

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.WorkerThread
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.kuromelabs.kurome.models.Device
import com.kuromelabs.kurome.services.KuromeService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

class DeviceRepository @Inject constructor(private val deviceDao: DeviceDao, @ApplicationContext private val context: Context) {
    val savedDevices: Flow<List<Device>> = deviceDao.getAllDevices()
    val serviceDevices: MutableSharedFlow<List<Device>> = MutableSharedFlow(1)
    lateinit var service: KuromeService

    init {
        CoroutineScope(Dispatchers.IO).launch { savedDevices.collectLatest { serviceDevices.emit(it) } }
        bindService()
    }

    @WorkerThread
    suspend fun insert(device: Device) {
        deviceDao.insert(device)
    }

    fun get(id: String): Device? {
        return deviceDao.getDevice(id)
    }

    private fun bindService() {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as KuromeService.LocalBinder).getService()
                Timber.d("Connected to service")
                service.lifecycleScope.launch {
                    service.connectedDeviceFlow.collect {
                        serviceDevices.emit(it)
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

@InstallIn(SingletonComponent::class)
@Module
class DatabaseModule {
    @Provides
    fun provideChannelDao(deviceDatabase: DeviceDatabase): DeviceDao {
        return deviceDatabase.deviceDao()
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context): DeviceDatabase {
        return Room.databaseBuilder(
            appContext,
            DeviceDatabase::class.java,
            "device_database"
        ).build()
    }
}
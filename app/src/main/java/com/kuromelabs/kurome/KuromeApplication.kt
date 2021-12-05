package com.kuromelabs.kurome

import android.app.Application
import com.kuromelabs.kurome.database.DeviceDatabase
import com.kuromelabs.kurome.database.DeviceRepository
import timber.log.Timber

class KuromeApplication : Application() {
    val database by lazy { DeviceDatabase.getDatabase(this) }
    val repository by lazy { DeviceRepository(database.deviceDao()) }
    override fun onCreate() {
        Timber.plant(Timber.DebugTree())
        super.onCreate()
    }
}
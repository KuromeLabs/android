package com.noirelabs.kurome

import android.app.Application
import com.noirelabs.kurome.database.DeviceDatabase
import com.noirelabs.kurome.database.DeviceRepository

class KuromeApplication : Application() {
    val database by lazy { DeviceDatabase.getDatabase(this) }
    val repository by lazy { DeviceRepository(database.deviceDao()) }
}
package com.kuromelabs.kurome.application.data_source

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kuromelabs.kurome.domain.Device

@Database(entities = [Device::class], version = 1, exportSchema = false)
abstract class DeviceDatabase : RoomDatabase() {
    companion object {
        const val DATABASE_NAME = "device_database"
    }

    abstract val deviceDao: DeviceDao
}
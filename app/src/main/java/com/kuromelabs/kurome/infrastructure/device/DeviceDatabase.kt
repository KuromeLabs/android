package com.kuromelabs.kurome.infrastructure.device

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kuromelabs.kurome.application.devices.Device
import com.kuromelabs.kurome.application.devices.DeviceDao

@Database(entities = [Device::class], version = 1, exportSchema = false)
abstract class DeviceDatabase : RoomDatabase() {
    companion object {
        const val DATABASE_NAME = "device_database"
    }

    abstract val deviceDao: DeviceDao
}
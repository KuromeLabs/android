package com.kuromelabs.kurome.infrastructure.device

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kuromelabs.kurome.application.devices.Device
import com.kuromelabs.kurome.application.devices.DeviceConverters
import com.kuromelabs.kurome.application.devices.DeviceDao

@Database(entities = [Device::class], version = 1, exportSchema = false)
@TypeConverters(DeviceConverters::class)
abstract class DeviceDatabase : RoomDatabase() {
    companion object {
        const val DATABASE_NAME = "device_database"
    }

    abstract val deviceDao: DeviceDao
}
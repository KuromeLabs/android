package com.kuromelabs.kurome.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kuromelabs.kurome.models.Device

@Database(entities = [Device::class], version = 1, exportSchema = false)
abstract class DeviceDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao
}
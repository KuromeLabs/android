package com.kuromelabs.kurome.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kuromelabs.kurome.models.Device

@Database(entities = [Device::class], version = 1, exportSchema = false)
abstract class DeviceDatabase : RoomDatabase() {
    companion object {
        private var INSTANCE: DeviceDatabase? = null
        fun getDatabase(context: Context): DeviceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context,
                    DeviceDatabase::class.java,
                    "device_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
    abstract fun deviceDao(): DeviceDao
}
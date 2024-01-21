package com.kuromelabs.kurome.application.devices

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(device: Device)

    @Query("SELECT * FROM device_table WHERE id = :id")
    fun getDevice(id: String): Device?

    @Query("SELECT * FROM device_table ORDER BY name ASC")
    fun getAllDevicesAsFlow(): Flow<List<Device>>

    @Query("SELECT * from device_table ORDER BY name ASC")
    fun getAllDevices(): List<Device>
}
package com.kuromelabs.kurome.database

import androidx.room.*
import com.kuromelabs.kurome.models.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: Device)

    @Query("SELECT * FROM device_table WHERE id = :id")
    fun getDevice(id: String): Device?

    @Query("SELECT * FROM device_table ORDER BY name ASC")
    fun getAllDevices(): Flow<List<Device>>
}
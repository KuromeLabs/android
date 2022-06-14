package com.kuromelabs.kurome.application.data_source

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kuromelabs.kurome.domain.Device
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: Device)

    @Query("SELECT * FROM device_table WHERE id = :id")
    suspend fun getDevice(id: String): Device?

    @Query("SELECT * FROM device_table ORDER BY name ASC")
    fun getAllDevices(): Flow<List<Device>>
}
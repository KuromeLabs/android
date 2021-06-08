package com.noirelabs.kurome.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.noirelabs.kurome.models.Device
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(device: Device)

    @Query("SELECT * FROM device_table ORDER BY name ASC")
    fun getAllDevices(): Flow<List<Device>>
}
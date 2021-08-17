package com.kuromelabs.kurome.database

import androidx.room.*
import com.kuromelabs.kurome.models.Device
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(device: Device)

    @Query("UPDATE device_table SET isPaired = :isPaired WHERE id = :id")
    suspend fun setPaired(id: String, isPaired: Boolean): Int

    @Query("UPDATE device_table SET isConnected = :isConnected WHERE id = :id")
    suspend fun setConnected(id: String, isConnected: Boolean): Int

    @Query("SELECT * FROM device_table ORDER BY name ASC")
    fun getAllDevices(): Flow<List<Device>>
}
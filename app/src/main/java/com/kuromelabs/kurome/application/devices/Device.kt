package com.kuromelabs.kurome.application.devices

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "device_table")
data class Device(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String
) {


//    fun disconnect() {
//        link?.close()
//        packetJob?.cancel()
//    }



}

package com.noirelabs.kurome.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName="device_table")
data class Device(@PrimaryKey @ColumnInfo(name = "name") val name: String, val id: String)

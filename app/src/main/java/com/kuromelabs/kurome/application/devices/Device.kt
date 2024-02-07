package com.kuromelabs.kurome.application.devices

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import javax.security.cert.X509Certificate


@Entity(tableName = "device_table")
data class Device(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "certificate") val certificate: X509Certificate? = null
) {

}

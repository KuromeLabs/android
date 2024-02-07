package com.kuromelabs.kurome.application.devices

import androidx.room.TypeConverter
import javax.security.cert.X509Certificate

class DeviceConverters {
    @TypeConverter
    fun fromX509Certificate(certificate: X509Certificate?): String? {
        return certificate?.encoded?.toString(Charsets.ISO_8859_1)
    }

    @TypeConverter
    fun toX509Certificate(certificate: String?): X509Certificate? {
        return certificate?.let { X509Certificate.getInstance(it.toByteArray(Charsets.ISO_8859_1)) }
    }
}
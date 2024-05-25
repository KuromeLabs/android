package com.kuromelabs.kurome.application.devices

import androidx.room.TypeConverter
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class DeviceConverters {
    @TypeConverter
    fun fromX509Certificate(certificate: X509Certificate?): String? {
        return certificate?.encoded?.toString(Charsets.ISO_8859_1)
    }

    @TypeConverter
    fun toX509Certificate(certificate: String?): X509Certificate? {
        if (certificate == null) return null
        return  CertificateFactory.getInstance("X.509")
            .generateCertificate(certificate.byteInputStream(Charsets.ISO_8859_1)) as (X509Certificate)
    }
}
package com.kuromelabs.kurome.services


import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.GZIPInputStream
import kotlin.text.Charsets.UTF_8


class ForegroundConnectionServiceTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun byteArrayToGzip_testIsValidGzipMagicNumber() {
        val string = "test string"
        val service = ForegroundConnectionService()
        val gzipped = service.byteArrayToGzip(string.toByteArray())
        assertEquals(gzipped[0].toUByte().toInt(), 0x1f)
        assertEquals(gzipped[1].toUByte().toInt(), 0x8b)
    }

    @Test
    fun byteArrayToGzip_testIsDecompressedStringValid() {
        val string = "test string"
        val service = ForegroundConnectionService()
        val gzipped = service.byteArrayToGzip(string.toByteArray())
        val gzipDecompressed = GZIPInputStream(gzipped.inputStream()).bufferedReader(UTF_8).readText()
        assertEquals(gzipDecompressed, "test string")
    }



}
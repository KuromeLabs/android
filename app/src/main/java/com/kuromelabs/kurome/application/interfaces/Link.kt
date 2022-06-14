package com.kuromelabs.kurome.application.interfaces

import java.nio.ByteBuffer

interface Link {
    suspend fun receive(buffer: ByteArray, size: Int): Int
    suspend fun send(buffer: ByteBuffer)
    suspend fun close()
}
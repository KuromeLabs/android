package com.kuromelabs.kurome.network

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SocketInstanceTest {
    var socket: SocketInstance? = null

    var testServerSocket: Socket = Socket()
    var `is`: InputStream? = null
    var out: OutputStream? = null

    @Before
    fun setUp() {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        socket = SocketInstance()
        socket!!.startConnection("127.0.0.1", port)
        testServerSocket = serverSocket.accept()
        `is` = testServerSocket.getInputStream()
        out = testServerSocket.getOutputStream()
    }

    @After
    fun tearDown() {
        testServerSocket.close()
        socket?.stopConnection()
    }

    @Test
    fun sendMessage_testServerReceivedString_smallInput() {
        socket?.sendMessage("test string".toByteArray())
        val sizeBytes = ByteArray(4)
        `is`?.read(sizeBytes)
        val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
        val messageByte = ByteArray(size)
        val bytesRead = `is`?.read(messageByte)
        val dataString = String(messageByte, 0, bytesRead!!)
        assertEquals("test string", dataString)
    }

    @Test
    fun littleEndianPrefixedByteArray_testValidLittleEndianPrefix_smallInput() {
        val string = "test string"
        val stringLength = string.length
        val stringPrefixed = socket?.littleEndianPrefixedByteArray(string.toByteArray())
        val sizeBytes = stringPrefixed!!.copyOfRange(0, 4)
        val x = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(x, stringLength)
        val stringBytes = stringPrefixed.copyOfRange(4, stringPrefixed.size)
        assertEquals(String(stringBytes), string)
    }

    @Test
    fun littleEndianPrefixedByteArray_testValidLittleEndianPrefix_bigInput() {
        val string = "test string".repeat(500000)
        val stringLength = string.length
        val stringPrefixed = socket?.littleEndianPrefixedByteArray(string.toByteArray())
        val sizeBytes = stringPrefixed!!.copyOfRange(0, 4)
        val x = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(x, stringLength)
        val stringBytes = stringPrefixed.copyOfRange(4, stringPrefixed.size)
        assertEquals(String(stringBytes), string)
    }

    @Test
    fun sendMessage_testServerReceivedString_emptyInput() {
        socket?.sendMessage("".toByteArray())
        val sizeBytes = ByteArray(4)
        `is`?.read(sizeBytes)
        val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
        val messageByte = ByteArray(size)
        val bytesRead = `is`?.read(messageByte)
        val dataString = String(messageByte, 0, bytesRead!!)
        assertEquals("", dataString)
    }

    @Test
    fun receiveMessage_testClientReceivedString_smallInput() {
        val string = "test string"
        val prefixedString = socket!!.littleEndianPrefixedByteArray(string.toByteArray())
        out!!.write(prefixedString)
        val received = socket!!.receiveMessage()
        assertEquals(string, received)
    }
}
package com.kuromelabs.kurome.network

import androidx.test.core.app.ActivityScenario.launch
import com.kuromelabs.kurome.services.ForegroundConnectionService
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class LinkTest {
    var socket: Link? = null
    private var out: ByteWriteChannel? = null
    private var `in`: ByteReadChannel? = null
    private val testDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()
    private val testScope = TestCoroutineScope(testDispatcher)
    private var testServerSocket: Socket? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        testScope.launch {
            val selector = ActorSelectorManager(testDispatcher)
            val serverSocket = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
            socket = Link()
            socket!!.startConnection("127.0.0.1", 0)
            testServerSocket = serverSocket.accept()
            out = testServerSocket!!.openWriteChannel(true)
            `in` = testServerSocket!!.openReadChannel()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun sendMessage_testServerReceivedString_smallInput() {
        testScope.launch {
            socket?.sendMessage("test string".repeat(500000).toByteArray(), false)
            val sizeBytes = ByteArray(4)
            `in`?.readFully(sizeBytes)
            val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
            val messageByte = ByteArray(size)
            `in`?.readFully(messageByte)
            val dataString = String(messageByte, 0, messageByte.size)
            assertEquals("test string", dataString)
        }
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
        testScope.launch {
            socket?.sendMessage("".toByteArray(), false)
            val sizeBytes = ByteArray(4)
            `in`?.readFully(sizeBytes)
            val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
            val messageByte = ByteArray(size)
            `in`?.readFully(messageByte)
            val dataString = String(messageByte, 0, messageByte.size)
            assertEquals("", dataString)
        }
    }

    @Test
    fun receiveMessage_testClientReceivedString_smallInput() {
        testScope.launch {
            val string = "test string"
            val prefixedString = socket!!.littleEndianPrefixedByteArray(string.toByteArray())
            out!!.writeFully(prefixedString)
            val received = socket!!.receiveMessage()
            assertEquals(string, received)
        }
    }

    @Test
    fun byteArrayToGzip_testIsGzip_bigInput(){
        val string = "test string".repeat(500000)
        val result = socket!!.byteArrayToGzip(string.toByteArray())
        assertEquals(result[0].toUByte().toInt(), 0x1f)
        assertEquals(result[1].toUByte().toInt(), 0x8b)
    }

    @Test
    fun byteArrayToGzip_testIsDecompressedStringValid() {
        val string = "test string".repeat(500000)
        val result = socket!!.byteArrayToGzip(string.toByteArray())
        val decompressed = GZIPInputStream(result.inputStream()).bufferedReader(Charsets.UTF_8).readText()
        assertEquals(decompressed, string)
    }


    @Test
    fun `client sends gzipped message, server checks if it's valid`() {
        testScope.launch {
            socket?.sendMessage("".toByteArray(), true)
            val sizeBytes = ByteArray(4)
            `in`?.readFully(sizeBytes)
            val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
            val messageByte = ByteArray(size)
            `in`?.readFully(messageByte)
            val decompressed = GZIPInputStream(messageByte.inputStream()).bufferedReader(Charsets.UTF_8).readText()
            assertEquals("", decompressed)
        }
    }
}
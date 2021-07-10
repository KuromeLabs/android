package com.kuromelabs.kurome.services

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.kuromelabs.kurome.models.FileData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

@RunWith(AndroidJUnit4::class)
class ForegroundConnectionServiceAndroidTest {
    private var udpCaster: MulticastSocket? = null
    private var context: Context? = null
    private var serviceIntent: Intent? = null
    private var service: ForegroundConnectionService? = null
    private lateinit var serverSocket: ServerSocket

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Before
    fun startService() {
        udpCaster = MulticastSocket(33586)
        context = ApplicationProvider.getApplicationContext()
        serviceIntent = Intent(context, ForegroundConnectionService::class.java).putExtra("isTest", true)
        ContextCompat.startForegroundService(context!!, serviceIntent!!)
        val binder = serviceRule.bindService(serviceIntent!!)
        service = (binder as ForegroundConnectionService.LocalBinder).getService()
        serverSocket = ServerSocket(33587)
    }

    @After
    fun stopService() {
        serverSocket.close()
        udpCaster!!.close()
        serviceRule.unbindService()
        context!!.stopService(serviceIntent)
    }

    @Test
    fun startedService_testGetStorageSpaceInformation() {
        val s = connect()
        val input = s.getInputStream()
        val out = s.getOutputStream()
        sendMessage(out, "request:info:space")
        val dataString = receiveMessage(input)
        assertEquals(StatFs(Environment.getDataDirectory().path).totalBytes, dataString.split(':')[0].toLong())
        assertEquals(StatFs(Environment.getDataDirectory().path).availableBytes, dataString.split(':')[1].toLong())
    }

    @Test
    fun startedService_testGetStorageDirectory() {
        val s = connect()
        val input = s.getInputStream()
        val out = s.getOutputStream()
        sendMessage(out, "request:info:directory:/")
        val dataString = receiveMessage(input)

        val fileDataList: ArrayList<FileData> = ArrayList()
        val envPath = Environment.getExternalStorageDirectory().path
        val files = File("$envPath/").listFiles()
        if (files != null)
            for (file in files) {
                fileDataList.add(FileData(file.name, file.isDirectory, file.length()))
            }
        assertEquals(dataString, Json.encodeToString(fileDataList))
    }

    private fun connect(): Socket {
        var socket: Socket? = null
        val message = "com.kuromelabs.kurome:127.0.0.1:id"
        val group = InetAddress.getByName("235.132.20.12")
        udpCaster!!.joinGroup(group)
        val bytes = message.toByteArray(StandardCharsets.UTF_8)
        val castMessage = DatagramPacket(bytes, bytes.size, group, 33586)
        /*if UDP fails, cast & try to connect again*/
        serverSocket.soTimeout = 5
        while (socket == null) {
            try {
                udpCaster!!.send(castMessage)
                socket = serverSocket.accept()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val dataString = receiveMessage(socket.getInputStream())
        assertEquals(Build.MODEL.toString(), dataString)
        udpCaster!!.leaveGroup(group)
        return socket
    }

    private fun receiveMessage(input: InputStream): String {
        val sizeBytes = ByteArray(4)
        input.read(sizeBytes)
        val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
        val messageByte = ByteArray(size)
        val bytesRead = input.read(messageByte)
        if (messageByte[0].toUByte().toInt() == 0x1f && messageByte[1].toUByte().toInt() == 0x8b) {
            return GZIPInputStream(messageByte.inputStream()).bufferedReader(Charsets.UTF_8).readText()
        }
        return String(messageByte, 0, bytesRead)
    }

    private fun sendMessage(out: OutputStream, message: String) {
        val array = message.toByteArray()
        val size = Integer.reverseBytes(array.size)
        val sizeBytes = ByteBuffer.allocate(4).putInt(size).array()
        out.write(sizeBytes + array)
    }
}
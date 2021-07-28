package com.kuromelabs.kurome.models

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.kuromelabs.kurome.Packets
import com.kuromelabs.kurome.network.TcpClient
import com.kuromelabs.kurome.network.UdpClient
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream


@Entity(tableName = "device_table")
data class Device(
    @PrimaryKey @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "id") val id: String,

    ) {
    constructor(name: String, id: String, context: Context) : this(name, id) {
        this.context = context
    }

    @Ignore
    private var context: Context? = null

    @Ignore
    private val job = SupervisorJob()

    @Ignore
    private val scope = CoroutineScope(Dispatchers.IO + job)

    @Ignore
    private var ip = String()

    @Ignore
    private val socket = TcpClient()
    suspend fun activate() {

        val udp = UdpClient(33586)
        while (job.isActive) {
            try {
                val udpMessage = udp.receiveUDPMessage("235.132.20.12").split(':')
                ip = udpMessage[1]
                socket.startConnection(ip, 33587)
                socket.sendMessage(Build.MODEL.toByteArray())
                var message = socket.receiveMessage()

                while (job.isActive && message != null) {
                    /*device wakes up briefly when receiving TCP so we acquire a partial wakelock until we reply*/
                    val pm: PowerManager = ContextCompat.getSystemService(context!!, PowerManager::class.java)!!
                    val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.kuromelabs.kurome: tcp wakelock")
                    wl.acquire(10 * 60 * 1000L /*10 minutes*/)
                    parseMessage(message)
                    wl.release()
                    message = socket.receiveMessage()
                }
            } catch (e: Exception) {
                Log.d("Kurome", e.toString())
            }
        }
    }

    fun byteArrayToGzip(str: ByteArray): ByteArray {
        Log.d("com.kuromelabs.kurome", String(str))
        val byteArrayOutputStream = ByteArrayOutputStream(str.size)
        val gzip = GZIPOutputStream(byteArrayOutputStream)
        gzip.write(str)
        gzip.close()
        val compressed = byteArrayOutputStream.toByteArray()
        byteArrayOutputStream.close()
        return compressed
    }

    suspend fun parseMessage(message: ByteArray) {
        val result: String
        val path: String? = if (message.size > 1) String(message, 1, message.size - 1) else null
        when (message[0]) {
            Packets.ACTION_GET_SPACE_INFO -> {
                val totalSpace = StatFs(Environment.getDataDirectory().path).totalBytes
                val availableSpace = StatFs(Environment.getDataDirectory().path).availableBytes
                result = "$totalSpace:$availableSpace"
                respondToRequest(result.toByteArray())
            }

            Packets.ACTION_GET_ENUMERATE_DIRECTORY -> {
                result = Json.encodeToString(getFilesInPathAsFileData(path!!))
                respondToRequest(result.toByteArray())
            }
            Packets.ACTION_WRITE_DIRECTORY -> {
                val dirPath = Environment.getExternalStorageDirectory().path + path!!
                val file = File(dirPath)
                respondToRequest(
                    if (file.mkdir())
                        byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
                    else
                        byteArrayOf(Packets.RESULT_ACTION_FAIL)
                )
            }
            Packets.ACTION_GET_FILE_TYPE -> {
                val file = File(Environment.getExternalStorageDirectory().path + path!!)
                respondToRequest(
                    if (file.exists())
                        if (file.isDirectory)
                            byteArrayOf(Packets.RESULT_FILE_IS_DIRECTORY)
                        else
                            byteArrayOf(Packets.RESULT_FILE_IS_FILE)
                    else
                        byteArrayOf(Packets.RESULT_FILE_NOT_FOUND)
                )
            }
            Packets.ACTION_DELETE -> {
                val file = File(Environment.getExternalStorageDirectory().path + path!!)
                respondToRequest(
                    if (file.deleteRecursively()) byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
                    else byteArrayOf(Packets.RESULT_ACTION_FAIL)
                )
            }
            Packets.ACTION_SEND_TO_SERVER -> {
                val fileSocket = TcpClient()
                Log.d("kurome", "sending file: " + path)
                CoroutineScope(Dispatchers.IO).launch {
                    fileSocket.startConnection(ip, 33588)
                    fileSocket.sendFile(Environment.getExternalStorageDirectory().path + path)
                    fileSocket.stopConnection()
                    this.cancel()
                }
            }
            Packets.ACTION_GET_FILE_INFO -> {
                val file = File(Environment.getExternalStorageDirectory().path + path)
                result = Json.encodeToString(FileData(file.name, file.isDirectory, file.length()))
                respondToRequest(result.toByteArray())
            }
        }
    }

    suspend fun respondToRequest(response: ByteArray) {
        val final = if (response.size > 100) byteArrayToGzip(response) else response
        socket.sendMessage(final)
    }

    fun getFilesInPathAsFileData(path: String): ArrayList<FileData> {
        val fileDataList: ArrayList<FileData> = ArrayList()
        val envPath = Environment.getExternalStorageDirectory().path
        val files = File(envPath + path).listFiles()
        if (files != null)
            for (file in files) {
                fileDataList.add(FileData(file.name, file.isDirectory, file.length()))
            }
        return fileDataList
    }

    fun deactivate(){
        socket.stopConnection()
        job.cancelChildren()
    }
}

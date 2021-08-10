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
import java.io.File


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


        while (job.isActive) {
            try {
                val udpMessage = UdpClient(33586).receiveUDPMessage("235.132.20.12").split(':')
                ip = udpMessage[1]
                socket.startConnection(ip, 33587)
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
                job.cancel()
            }
        }
    }


    suspend fun parseMessage(bytes: ByteArray) {
        val result: String
        val message: String? = if (bytes.size > 1) String(bytes, 1, bytes.size - 1) else null
        when (bytes[0]) {
            Packets.ACTION_GET_SPACE_INFO -> {
                val totalSpace = StatFs(Environment.getDataDirectory().path).totalBytes
                val availableSpace = StatFs(Environment.getDataDirectory().path).availableBytes
                result = "$totalSpace:$availableSpace"
                socket.sendMessage(result.toByteArray(), true)
            }

            Packets.ACTION_GET_ENUMERATE_DIRECTORY -> {
                result = Json.encodeToString(getFilesInPathAsFileData(message!!))
                socket.sendMessage(result.toByteArray(), true)
            }
            Packets.ACTION_WRITE_DIRECTORY -> {
                val dirPath = Environment.getExternalStorageDirectory().path + message!!
                val file = File(dirPath)
                socket.sendMessage(
                    if (file.mkdir())
                        byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
                    else
                        byteArrayOf(Packets.RESULT_ACTION_FAIL), true
                )
            }
            Packets.ACTION_GET_FILE_TYPE -> {
                val file = File(Environment.getExternalStorageDirectory().path + message!!)
                socket.sendMessage(
                    if (file.exists())
                        if (file.isDirectory)
                            byteArrayOf(Packets.RESULT_FILE_IS_DIRECTORY)
                        else
                            byteArrayOf(Packets.RESULT_FILE_IS_FILE)
                    else
                        byteArrayOf(Packets.RESULT_FILE_NOT_FOUND), true
                )
            }
            Packets.ACTION_DELETE -> {
                val file = File(Environment.getExternalStorageDirectory().path + message!!)
                socket.sendMessage(
                    if (file.deleteRecursively()) byteArrayOf(Packets.RESULT_ACTION_SUCCESS)
                    else byteArrayOf(Packets.RESULT_ACTION_FAIL), true
                )
            }
            Packets.ACTION_SEND_TO_SERVER -> {
                val fileSocket = TcpClient()
                val path = message!!.split(':')[0]
                val offset = message.split(':')[1].toLong()
                val size = message.split(':')[2].toInt()

                Log.d("kurome", "sending file: " + message)
                CoroutineScope(Dispatchers.IO).launch {
                    fileSocket.startConnection(ip, 33588)
                    fileSocket.sendFileBuffer(Environment.getExternalStorageDirectory().path + path, offset, size)
                    fileSocket.stopConnection()
                    this.cancel()
                }
            }
            Packets.ACTION_GET_FILE_INFO -> {
                val file = File(Environment.getExternalStorageDirectory().path + message)
                result = Json.encodeToString(FileData(file.name, file.isDirectory, file.length()))
                socket.sendMessage(result.toByteArray(), true)
            }
            Packets.ACTION_GET_DEVICE_NAME -> {
                socket.sendMessage(Build.MODEL.toByteArray(), true)
            }
            Packets.ACTION_GET_DEVICE_ID -> {
                socket.sendMessage(getGuid(context!!).toByteArray(), true)
            }
        }
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

    fun deactivate() {
        socket.stopConnection()
        job.cancel()
    }
}

package com.kuromelabs.kurome.services

import android.app.*
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.activities.MainActivity
import com.kuromelabs.kurome.models.FileData
import com.kuromelabs.kurome.network.SocketInstance
import com.kuromelabs.kurome.network.UdpClient
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

const val ACTION_GET_ENUMERATE_DIRECTORY: Byte = 1
const val ACTION_GET_SPACE_INFO: Byte = 2
const val ACTION_GET_FILE_TYPE: Byte = 3
const val ACTION_WRITE_DIRECTORY: Byte = 4

class ForegroundConnectionService : Service() {
    private val CHANNEL_ID = "ForegroundServiceChannel"
    private val socket = SocketInstance()
    private val udp = UdpClient(33586)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val binder: IBinder = LocalBinder()


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        //     val input = intent.getStringExtra("inputExtra")
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kurome is running in the background")
            .setContentText("Tap to hide notification (no effect on functionality)")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)

        scope.launch {
            while (isActive) {
                try {
                    val udpMessage = udp.receiveUDPMessage("235.132.20.12").split(':')
                    socket.startConnection(udpMessage[1], 33587)
                    socket.sendMessage(Build.MODEL.toByteArray())
                    var message = receiveMessage()

                    while (isActive && message != null) {
                        /*device wakes up briefly when receiving TCP so we acquire a partial wakelock until we reply*/
                        val pm: PowerManager =
                            ContextCompat.getSystemService(applicationContext, PowerManager::class.java)!!
                        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.kuromelabs.kurome: tcp wakelock")
                        wl.acquire(10 * 60 * 1000L /*10 minutes*/)
                        val response = parseMessage(message)
                        respondToRequest(response)
                        wl.release()
                        message = receiveMessage()
                    }
                } catch (e: Exception) {
                    Log.d("Kurome", e.toString())
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        socket.stopConnection()
        udp.close()
        job.cancel()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
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

    fun receiveMessage(): ByteArray? {
        return try {
            socket.receiveMessage()
        } catch (e: Exception) {
            e.printStackTrace()
            socket.stopConnection()
            null
        }
    }

    fun parseMessage(message: ByteArray): ByteArray {
        var result = String()
        when (message[0]) {
            ACTION_GET_SPACE_INFO -> {
                val totalSpace = StatFs(Environment.getDataDirectory().path).totalBytes
                val availableSpace = StatFs(Environment.getDataDirectory().path).availableBytes
                result = "$totalSpace:$availableSpace"
            }
            ACTION_GET_ENUMERATE_DIRECTORY -> {
                val path = String(message, 1, message.size-1)
                result = Json.encodeToString(getFilesInPathAsFileData(path))
            }
            ACTION_WRITE_DIRECTORY -> {
                val path = String(message, 1, message.size-1)
                val dirPath = Environment.getExternalStorageDirectory().path + path
                val file = File(dirPath)
                if (file.mkdir()) result = "success"
            }
            ACTION_GET_FILE_TYPE -> {
                val path = String(message, 1, message.size-1)
                val file = File(Environment.getExternalStorageDirectory().path + path)
                result = if (file.exists())
                    if (file.isDirectory)
                        "directory"
                    else
                        "file"
                else
                    "doesnotexist"
            }
        }
        return result.toByteArray()
    }

    fun respondToRequest(response: ByteArray) {
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

    inner class LocalBinder : Binder() {
        fun getService(): ForegroundConnectionService = this@ForegroundConnectionService
    }
}
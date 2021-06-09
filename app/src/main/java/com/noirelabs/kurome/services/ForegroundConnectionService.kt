package com.noirelabs.kurome.services

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.noirelabs.kurome.models.FileData
import com.noirelabs.kurome.R
import com.noirelabs.kurome.activities.MainActivity
import com.noirelabs.kurome.network.SocketInstance
import com.noirelabs.kurome.network.UdpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File


class ForegroundConnectionService : Service() {
    private val CHANNEL_ID = "ForegroundServiceChannel"
    private val socket = SocketInstance()
    private val udp = UdpClient()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
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
        //do heavy work on a background thread
        //stopSelf();;
        scope.launch {
            val s: List<String> = udp.receiveUDPMessage("235.132.20.12",33586).split(':')
            socket.startConnection(s[1], 33587)
            socket.sendMessage(Build.MODEL)
            while (true) {
                val message: String = socket.receiveMessage()
                val pm: PowerManager = ContextCompat.getSystemService(applicationContext, PowerManager::class.java)!!
                val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kurome: tcp wakelock")
                wl.acquire(10*60*1000L /*10 minutes*/)
                when {
                    message == "request:info:space" -> {
                        val totalSpace = StatFs(Environment.getDataDirectory().path).totalBytes
                        val availableSpace = StatFs(Environment.getDataDirectory().path).availableBytes
                        socket.sendMessage("$totalSpace:$availableSpace")
                    }
                    message.contains("Exception") -> {
                        socket.stopConnection()
                        return@launch
                    }
                    message.startsWith("request:info:directory") -> {
                            val fileDataList: ArrayList<FileData> = ArrayList()
                            val files = File(Environment.getExternalStorageDirectory().path).listFiles()
                            for (file in files){
                                fileDataList.add(FileData(file.name, file.isDirectory, file.length()))
                            }
                            val str = Json.encodeToString(fileDataList)
                            socket.sendMessage(str)
                    }
                }
                wl.release()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        socket.stopConnection()
        job.cancel()
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
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
}
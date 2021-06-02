package com.noirelabs.kurome.services

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import androidx.core.app.NotificationCompat
import com.noirelabs.kurome.R
import com.noirelabs.kurome.activities.MainActivity
import com.noirelabs.kurome.network.SocketInstance


class ForegroundConnectionService : Service() {
    val CHANNEL_ID = "ForegroundServiceChannel"

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
        Thread {
            val socket = SocketInstance()
            val s: List<String> = socket.receiveUDPMessage("235.132.20.12",33586).split(':')
            socket.startConnection(s[1], 33587)
            while (true) {
                val message: String = socket.receiveMessage()
                if (message == "request:info:space") {
                    val totalSpace = StatFs(Environment.getDataDirectory().path).totalBytes
                    val availableSpace = StatFs(Environment.getDataDirectory().path).availableBytes
                    socket.sendMessage("$totalSpace:$availableSpace")
                }
            }
        }.start()
        return START_NOT_STICKY
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
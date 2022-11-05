package com.kuromelabs.kurome.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.application.interfaces.DeviceRepository
import com.kuromelabs.kurome.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KuromeService : LifecycleService() {
    private val CHANNEL_ID = "ForegroundServiceChannel"

    //    @Inject
//    lateinit var linkProvider: LinkProvider
    @Inject
    lateinit var repository: DeviceRepository
    private var isServiceStarted = false
    private val binder: IBinder = LocalBinder()

    @Inject
    lateinit var tcpListenerService: TcpListenerService

    @Inject
    lateinit var udpListenerService: UdpListenerService


    override fun onCreate() {
        super.onCreate()
        tcpListenerService.start()
        udpListenerService.start()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //     val input = intent.getStringExtra("inputExtra")
        isServiceStarted = true
        startForeground(1, createForegroundNotification())
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    private fun createForegroundNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kurome is running in the background")
            .setContentText("Tap to hide notification (no effect on functionality)")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentIntent(pendingIntent)
            .build()
    }


    override fun onDestroy() {
        super.onDestroy()
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

    inner class LocalBinder : Binder() {
        fun getService(): KuromeService = this@KuromeService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        if (!isServiceStarted)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        return binder
    }
}
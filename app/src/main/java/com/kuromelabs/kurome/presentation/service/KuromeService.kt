package com.kuromelabs.kurome.presentation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.application.devices.DeviceRepository
import com.kuromelabs.kurome.application.interfaces.SecurityService
import com.kuromelabs.kurome.infrastructure.device.DeviceService
import com.kuromelabs.kurome.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

@AndroidEntryPoint
class KuromeService : LifecycleService() {
    private val channelId = "ForegroundServiceChannel"
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var isServiceStarted = false
    @Inject
    lateinit var deviceService: DeviceService
    override fun onCreate() {
        super.onCreate()
        deviceService.start()
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
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Kurome is running in the background")
            .setContentText("Tap to hide notification (no effect on functionality)")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentIntent(pendingIntent)
            .build()
    }


    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
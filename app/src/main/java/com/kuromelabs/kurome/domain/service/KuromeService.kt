package com.kuromelabs.kurome.domain.service

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
import androidx.lifecycle.lifecycleScope
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.domain.model.Device
import com.kuromelabs.kurome.domain.repository.DeviceRepository
import com.kuromelabs.kurome.domain.util.link.LinkProvider
import com.kuromelabs.kurome.domain.util.link.LinkState
import com.kuromelabs.kurome.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class KuromeService : LifecycleService() {
    private val CHANNEL_ID = "ForegroundServiceChannel"
    @Inject
    lateinit var linkProvider: LinkProvider
    @Inject
    lateinit var repository: DeviceRepository
    private var isServiceStarted = false
    private val devicesMap = ConcurrentHashMap<String, Device>()
    private val binder: IBinder = LocalBinder()


    override fun onCreate() {
        super.onCreate()
        initializeMap()
        observeLinkProvider()
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
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kurome is running in the background")
            .setContentText("Tap to hide notification (no effect on functionality)")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun initializeMap() {
        lifecycleScope.launch {
            repository.getSavedDevices().collectLatest {
                Timber.d("Observed size: ${it.size}")
                for (device in it) {
                    devicesMap[device.id] = device
                    Timber.d("devicesMap contents: ${devicesMap.keys}")
                }
            }
        }
    }

    private fun observeLinkProvider() {
        lifecycleScope.launch {
            linkProvider.observeLinks().collect {
                val link = it.link
                when (it.state) {
                    LinkState.State.CONNECTED -> {
                        var device = devicesMap[link.deviceId]
                        if (device != null) {
                            Timber.d("Known device: $device")
                            device.setLink(link)
                            lifecycleScope.launch { repository.setServiceDevices(devicesMap.values.toList()) }
                        } else {
                            Timber.d("Unknown device: ${link.deviceId}")
                            device = Device(link.deviceName, link.deviceId)
                            device.setLink(link)
                            devicesMap[link.deviceId] = device
                            lifecycleScope.launch { repository.setServiceDevices(devicesMap.values.toList()) }
                        }
                    }
                    LinkState.State.DISCONNECTED -> {
                        val device = devicesMap[link.deviceId]
                        if (device != null) {
                            if (!device.isPaired)
                                devicesMap.remove(link.deviceId)
                            device.disconnect()
                            lifecycleScope.launch { repository.setServiceDevices(devicesMap.values.toList()) }
                            Timber.d("Device disconnected: $device")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        linkProvider.onStop()
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
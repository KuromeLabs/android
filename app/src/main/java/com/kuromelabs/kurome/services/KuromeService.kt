package com.kuromelabs.kurome.services

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
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.kuromelabs.kurome.KuromeApplication
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.UI.MainActivity
import com.kuromelabs.kurome.database.DeviceRepository
import com.kuromelabs.kurome.models.Device
import com.kuromelabs.kurome.network.Link
import com.kuromelabs.kurome.network.LinkProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean


class KuromeService : LifecycleService() {
    private val CHANNEL_ID = "ForegroundServiceChannel"
    private lateinit var linkProvider: LinkProvider
    private lateinit var repository: DeviceRepository
    private var isWifiConnected: AtomicBoolean = AtomicBoolean(false)
    private var isServiceStarted = false
    private val lifecycleOwner = this
    private val devicesMap = ConcurrentHashMap<String, Device>()
    private val binder: IBinder = LocalBinder()

    private val _connectedDeviceFlow = MutableSharedFlow<List<Device>>(1)
    val connectedDeviceFlow: SharedFlow<List<Device>> = _connectedDeviceFlow

    override fun onCreate() {
        super.onCreate()
        repository = (application as KuromeApplication).repository
        initializeObserver()
        linkProvider = LinkProvider(baseContext, lifecycleScope)
        linkProvider.addLinkListener(deviceConnectionListener)
        linkProvider.listening = true
        linkProvider.initializeUdpListener("235.132.20.12", 33586)
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

    private fun initializeObserver() {
        val observer = Observer<List<Device>> {
            Timber.d("Observed size: ${it.size}")
            for (device in it) {
                devicesMap[device.id] = device
                Timber.d("devicesMap contents: ${devicesMap.keys}")
            }

        }
        repository.savedDevices.asLiveData().observe(lifecycleOwner, observer)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private val deviceConnectionListener = object : LinkProvider.LinkListener {
        override fun onLinkConnected(packetString: String?, link: Link?) {
            val split = packetString!!.split(':')
            val ip = split[1]
            val name = split[2]
            val id = split[3]
            var device = devicesMap[id]
            if (device != null) {
                device.isConnected = true
                lifecycleScope.launch { _connectedDeviceFlow.emit(devicesMap.values.toList()) }
                Timber.d("Known device: $device")
                device.context = applicationContext
                device.setLink(link!!)
            } else {
                Timber.d("Unknown device: $id")
                device = Device(name, id)

            }
        }

        override fun onLinkDisconnected(id: String?, link: Link?) {
            val device = devicesMap[id]
            if (device != null) {
                device.isConnected = false
                lifecycleScope.launch { _connectedDeviceFlow.emit(devicesMap.values.toList()) }
                Timber.d("Device disconnected: $device")
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
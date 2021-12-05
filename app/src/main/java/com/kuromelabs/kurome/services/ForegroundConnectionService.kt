package com.kuromelabs.kurome.services

import android.app.*
import android.content.Intent
import android.net.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.kuromelabs.kurome.KuromeApplication
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.UI.MainActivity
import com.kuromelabs.kurome.database.DeviceRepository
import com.kuromelabs.kurome.models.Device
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList


class ForegroundConnectionService : LifecycleService(), Device.DeviceStatusListener {
    private val CHANNEL_ID = "ForegroundServiceChannel"
    private val binder: IBinder = LocalBinder()

    private val connectedDevices = Collections.synchronizedList(ArrayList<Device>())
    private lateinit var repository: DeviceRepository
    private var isWifiConnected = false
    private var isServiceActive = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        repository = (application as KuromeApplication).repository
        val cm = ContextCompat.getSystemService(applicationContext, ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()


        val observer = Observer<List<Device>> {
            for (device in it) {
                if (device !in connectedDevices)
                    monitorDevice(device)
            }

        }
        cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            var runningJob: Job? = null
            override fun onLost(network: Network) {
                isWifiConnected = false
                runningJob?.cancel()
                CoroutineScope(Dispatchers.Main).launch {
                    repository.savedDevices.asLiveData().removeObserver(observer)
                }
                lifecycleScope.launch { killDevices() }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                isWifiConnected = true
                runningJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(3000)
                    if (runningJob!!.isActive)
                        repository.savedDevices.asLiveData().observeForever(observer)
                }
            }
        })
        isServiceActive = true
        return super.onStartCommand(intent, flags, startId)
    }

    fun monitorDevice(device: Device) {
        device.context = applicationContext
        device.listener = this
        device.activate()
    }

    suspend fun killDevices() {
        for (device in connectedDevices) {
            Timber.d("deactivating $device")
            device.deactivate()
        }
        connectedDevices.clear()
        repository.setConnectedDevices(connectedDevices)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceActive = false
        lifecycleScope.launch { killDevices() }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
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


    inner class LocalBinder : Binder() {
        fun getService(): ForegroundConnectionService = this@ForegroundConnectionService
    }

    override suspend fun onConnected(device: Device) {
        Timber.d("onConnected called $device")
        connectedDevices.add(device)
        repository.setConnectedDevices(connectedDevices)
    }

    override suspend fun onDisconnected(device: Device) {
        connectedDevices.remove(device)
        Timber.d("onDisconnected called $device")
        repository.setConnectedDevices(connectedDevices)
        if (isWifiConnected && isServiceActive)
            monitorDevice(device)
    }
}
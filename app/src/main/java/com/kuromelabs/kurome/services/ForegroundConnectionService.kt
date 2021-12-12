package com.kuromelabs.kurome.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*


class ForegroundConnectionService : LifecycleService(), Device.DeviceStatusListener {
    private val CHANNEL_ID = "ForegroundServiceChannel"
    private val binder: IBinder = LocalBinder()

    private val connectedDevices = Collections.synchronizedList(ArrayList<Device>())
    private lateinit var repository: DeviceRepository
    private var isWifiConnected = false
    private var isServiceActive = false
    private val lifecycleOwner = this

    override fun onCreate() {
        repository = (application as KuromeApplication).repository
        super.onCreate()
    }

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

        val cm = ContextCompat.getSystemService(applicationContext, ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()


        val observer = Observer<List<Device>> {
            for (device in it) {
                if (device !in connectedDevices) {
                    monitorDevice(device)
                    Timber.d("Observer: monitoring device $device")
                }
            }

        }

        cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            val data = repository.savedDevices.asLiveData()
            override fun onLost(network: Network) {
                isWifiConnected = false
                lifecycleScope.launch(Dispatchers.Main) {
                    data.removeObservers(lifecycleOwner)
                    killDevices()
                }
            }

            override fun onCapabilitiesChanged(net: Network, capabilities: NetworkCapabilities) {
                Timber.e("Monitor network capabilities: $capabilities network: $net")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                ) {
                    isWifiConnected = true
                    lifecycleScope.launch(Dispatchers.Main) {
                        Timber.d("observe job launched")
                        data.observe(lifecycleOwner, observer)
                        Timber.d("made observe call")
                    }
                }
            }
        })
        isServiceActive = true
        return super.onStartCommand(intent, flags, startId)
    }

    fun monitorDevice(device: Device) {
        Timber.d("monitorDevice called for $device")
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
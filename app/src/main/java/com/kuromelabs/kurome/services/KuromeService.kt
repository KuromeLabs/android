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
import android.os.Build
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
import com.kuromelabs.kurome.network.Link
import com.kuromelabs.kurome.network.LinkProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean


class KuromeService : LifecycleService() {
    private val CHANNEL_ID = "ForegroundServiceChannel"
    private lateinit var linkProvider: LinkProvider
    private lateinit var repository: DeviceRepository
    private var isWifiConnected: AtomicBoolean = AtomicBoolean(false)
    private var isServiceActive = false
    private val lifecycleOwner = this

    private val devicesMap = ConcurrentHashMap<String, Device>()

    override fun onCreate() {
        super.onCreate()
        repository = (application as KuromeApplication).repository
        initializeObserver()
        linkProvider = LinkProvider(baseContext)
        linkProvider.addLinkListener(deviceConnectionListener)
        linkProvider.listening = true
        try{
            linkProvider.initializeUdpListener("235.132.20.12", 33586)
        } catch (e: Exception) {
            Timber.e(e)
        }

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

        isServiceActive = true
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    private fun initializeObserver(){
        val observer = Observer<List<Device>> {
            Timber.d("Observed size: ${it.size}")
            for (device in it) {
                devicesMap[device.id] = device
                Timber.d("devicesMap contents: ${devicesMap.keys}")
            }

        }
        val cm = ContextCompat.getSystemService(applicationContext, ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            val data = repository.savedDevices.asLiveData()
            override fun onLost(network: Network) {
                Timber.e("Monitor network onLost: $network")
                isWifiConnected.set(false)
                lifecycleScope.launch(Dispatchers.Main) { data.removeObservers(lifecycleOwner) }
            }

            override fun onCapabilitiesChanged(net: Network, capabilities: NetworkCapabilities) {
                Timber.e("Monitor network capabilities: $capabilities network: $net")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                ) {
                    isWifiConnected.set(true)
                    lifecycleScope.launch(Dispatchers.Main) {
                        Timber.d("observe job launched")
                        data.observe(lifecycleOwner, observer)
                        Timber.d("made observe call")
                    }
                }
            }
        })
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private val deviceConnectionListener = object : LinkProvider.LinkListener {
        override fun onLinkConnected(packetString: String?, link: Link?) {
            val split = packetString!!.split(':')
            val ip = split[1]
            val name = split[2]
            val id = split[3]
            var device = devicesMap[id]
            if (device != null){
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
//                devicesMap.remove(id)
                Timber.d("Device disconnected: $device")
            }
        }

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
}
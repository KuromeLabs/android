package com.kuromelabs.kurome.services

import android.app.*
import android.content.Intent
import android.net.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import com.kuromelabs.kurome.KuromeApplication
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.UI.MainActivity
import com.kuromelabs.kurome.database.DeviceRepository
import com.kuromelabs.kurome.getGuid
import com.kuromelabs.kurome.models.Device
import com.kuromelabs.kurome.network.LinkProvider
import com.kuromelabs.kurome.network.Packets
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow


class ForegroundConnectionService : Service() {
    private val CHANNEL_ID = "ForegroundServiceChannel"
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val binder: IBinder = LocalBinder()
    private val activeDevices = ArrayList<Device>()
    private lateinit var repository: DeviceRepository

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
        repository = (application as KuromeApplication).repository
        val cm = ContextCompat.getSystemService(applicationContext, ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val linkProvider = LinkProvider()
        val observer = Observer<List<Device>> {
            for (device in it) {
                if (device.isPaired && !device.isConnected) {
                    scope.launch {
                        val controlLink = linkProvider.createControlLinkFromUdp(
                            "235.132.20.12",
                            33586
                        )
                        controlLink.sendMessage(byteArrayOf(Packets.ACTION_CONNECT) +
                                (Build.MODEL + ':' + getGuid(applicationContext!!)).toByteArray(), false)
                        if (controlLink.receiveMessage()[0] == Packets.RESULT_ACTION_SUCCESS) {
                            //repository.setPaired(device, true)
                            activeDevices.add(device)
                            device.isConnected = true
                            device.context = applicationContext
                            device.activate(controlLink, linkProvider)
                            repository.setConnectedDevices(activeDevices)
                        } else {
                            controlLink.stopConnection()
                            Log.e("kurome/service","Device connection failed: $device")
                        }
                    }
                }
            }
        }
        cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            var runningJob: Job? = null
            override fun onLost(network: Network) {
                runningJob?.cancel()
                CoroutineScope(Dispatchers.Main).launch {
                    repository.savedDevices.asLiveData().removeObserver(observer)
                }
                scope.launch { killDevices() }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                CoroutineScope(Dispatchers.Main).launch {
                    delay(5000)
                    repository.savedDevices.asLiveData().observeForever(observer)
                }
            }
        })
        return START_NOT_STICKY
    }


    suspend fun killDevices() {
        for (device in activeDevices) {
            device.deactivate()
            Log.d("kurome/service", "set device disconnected in repository")
        }
        activeDevices.clear()
        repository.setConnectedDevices(activeDevices)
    }

    override fun onDestroy() {
        super.onDestroy()
        GlobalScope.launch { killDevices() }
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


    inner class LocalBinder : Binder() {
        fun getService(): ForegroundConnectionService = this@ForegroundConnectionService
    }
}
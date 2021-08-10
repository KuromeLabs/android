package com.kuromelabs.kurome.services

import android.app.*
import android.content.Intent
import android.net.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.activities.MainActivity
import com.kuromelabs.kurome.models.Device
import kotlinx.coroutines.*


class ForegroundConnectionService : Service() {
    private val CHANNEL_ID = "ForegroundServiceChannel"
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val binder: IBinder = LocalBinder()
    private var ip = String()
    private val activeDevices = ArrayList<Device>()


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
        val cm = ContextCompat.getSystemService(applicationContext, ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            var runningJob: Job? = null
            override fun onLost(network: Network) {
                runningJob?.cancel()
                for (device in activeDevices)
                    device.deactivate()
                activeDevices.clear()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                if (runningJob == null || !runningJob!!.isActive)
                    runningJob = scope.launch {
                        delay(5000)
                        Device("test","test", applicationContext).let {
                            it.activate()
                            activeDevices.add(it)
                        }
                    }
            }
        })
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        for (device in activeDevices)
            device.deactivate()
        activeDevices.clear()
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
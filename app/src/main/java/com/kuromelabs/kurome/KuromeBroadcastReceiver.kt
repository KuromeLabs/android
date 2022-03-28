package com.kuromelabs.kurome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kuromelabs.kurome.infrastructure.service.KuromeService
import timber.log.Timber

class KuromeBroadcastReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("KuromeBroadcastReceiver: Boot Completed")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context?.startForegroundService(Intent(context, KuromeService::class.java))
            } else {
                context?.startService(Intent(context, KuromeService::class.java))
            }
        }
    }

}
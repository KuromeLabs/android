package com.noirelabs.kurome.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ForegroundConnectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
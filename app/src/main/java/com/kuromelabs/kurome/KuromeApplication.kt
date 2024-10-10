package com.kuromelabs.kurome

import android.app.Application
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class KuromeApplication : Application() {

    companion object {
        fun getBuildVersion(): Int = Build.VERSION.SDK_INT
    }

    override fun onCreate() {
        Timber.plant(Timber.DebugTree())
        super.onCreate()
    }
}
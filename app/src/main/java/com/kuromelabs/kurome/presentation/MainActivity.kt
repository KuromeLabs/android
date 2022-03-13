package com.kuromelabs.kurome.presentation

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.kuromelabs.kurome.UI.theme.KuromeTheme
import com.kuromelabs.kurome.presentation.devices.DeviceViewModel
import com.kuromelabs.kurome.presentation.devices.components.DevicesScreen
import com.kuromelabs.kurome.presentation.permissions.PermissionEvent
import com.kuromelabs.kurome.presentation.permissions.PermissionViewModel
import com.kuromelabs.kurome.domain.service.KuromeService
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    val deviceViewModel: DeviceViewModel by viewModels()
    private val permissionViewModel: PermissionViewModel by viewModels()
    private lateinit var service: KuromeService

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as KuromeService.LocalBinder).getService()
            Timber.d("Connected to service")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.d("Disconnected from service")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindService()
        setContent {
            KuromeTheme {
//                PermissionScreen({}, permissionViewModel)
                DevicesScreen(deviceViewModel, modifier = Modifier)
            }
        }
    }

    private fun checkFilePermissions(): PermissionEvent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            if (Environment.isExternalStorageManager())
                PermissionEvent.Granted(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
            else
                PermissionEvent.Revoked(Manifest.permission.MANAGE_EXTERNAL_STORAGE)

        else if (ContextCompat.checkSelfPermission(
                baseContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) PermissionEvent.Granted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else PermissionEvent.Revoked(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun onResume() {
        super.onResume()
        permissionViewModel.onEvent(checkFilePermissions())
    }

    private fun bindService() {
        Intent(baseContext, KuromeService::class.java).also { intent ->
            baseContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        baseContext.unbindService(serviceConnection)
    }
}
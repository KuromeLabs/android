package com.kuromelabs.kurome.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kuromelabs.kurome.UI.theme.KuromeTheme
import com.kuromelabs.kurome.domain.service.KuromeService
import com.kuromelabs.kurome.presentation.devices.components.DevicesScreen
import com.kuromelabs.kurome.presentation.permissions.PermissionEvent
import com.kuromelabs.kurome.presentation.permissions.components.PermissionScreen
import com.kuromelabs.kurome.presentation.util.Screen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KuromeTheme {
                val lifecycleOwner = LocalLifecycleOwner.current
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = if (!hasFilePermissions())
                        Screen.PermissionsScreen.route
                    else Screen.DevicesScreen.route
                ) {
                    composable(route = Screen.PermissionsScreen.route) {
                        PermissionScreen(navController)
                    }
                    composable(route = Screen.DevicesScreen.route) {
                        BackHandler(true) { finish() }
                        startService()
                        DevicesScreen(navController = navController, modifier = Modifier)
                    }
                }
                DisposableEffect(key1 = lifecycleOwner, effect = {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME)
                            if (!hasFilePermissions())
                                navController.navigate(Screen.PermissionsScreen.route)
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                })
            }
        }
    }


    private fun hasFilePermissions(): Boolean {
        return when (getFilePermissionEvent(baseContext)) {
            is PermissionEvent.Granted -> true
            is PermissionEvent.Revoked -> false
        }
    }

    private fun startService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            baseContext?.startForegroundService(Intent(baseContext, KuromeService::class.java))
        } else {
            baseContext?.startService(Intent(baseContext, KuromeService::class.java))
        }
    }
}

fun getFilePermissionEvent(context: Context): PermissionEvent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        if (Environment.isExternalStorageManager())
            PermissionEvent.Granted(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        else
            PermissionEvent.Revoked(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
    else if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    ) PermissionEvent.Granted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    else PermissionEvent.Revoked(Manifest.permission.WRITE_EXTERNAL_STORAGE)
}
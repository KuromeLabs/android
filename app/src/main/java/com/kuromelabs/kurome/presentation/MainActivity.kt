package com.kuromelabs.kurome.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import com.kuromelabs.kurome.presentation.service.KuromeService
import com.kuromelabs.kurome.presentation.ui.devices.AddDeviceScreen
import com.kuromelabs.kurome.presentation.ui.devices.DeviceDetailsScreen
import com.kuromelabs.kurome.presentation.ui.devices.DevicesScreen
import com.kuromelabs.kurome.presentation.ui.permissions.PermissionScreen
import com.kuromelabs.kurome.presentation.ui.permissions.PermissionStatus
import com.kuromelabs.kurome.presentation.ui.theme.KuromeTheme
import com.kuromelabs.kurome.presentation.util.Screen
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var serviceStarted: Boolean = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissionsMap = mutableStateMapOf<String, PermissionStatus>()
        updatePermissions(permissionsMap)
        setContent {
            KuromeTheme {
                Timber.d("Entering MainActivity Composition")

                val navController = rememberNavController()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.inverseOnSurface)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = if (permissionsMap.any { it.value != PermissionStatus.Granted })
                            Screen.PermissionsScreen.route
                        else Screen.DevicesScreen.route,
                    ) {
                        composable(route = Screen.PermissionsScreen.route) {
                            PermissionScreen(permissionsMap)
                        }
                        composable(Screen.DevicesScreen.route) {
                            BackHandler(true) { finish() }
                            startService()
                            DevicesScreen(navController = navController)
                        }
                        composable("${Screen.DeviceDetailScreen.route}/{deviceId}/{deviceName}") {
                            DeviceDetailsScreen(navController = navController)
                        }
                        composable(Screen.AddDeviceScreen.route) {
                            AddDeviceScreen(navController = navController)
                        }
                    }
                }
                val lifecycleOwner = LocalLifecycleOwner.current
                val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

                LaunchedEffect(lifecycleState) {
                    when (lifecycleState) {
                        Lifecycle.State.RESUMED -> {
                            Timber.d("MainActivity Composition Resumed")
                            updatePermissions(permissionsMap)
                            if (navController.currentBackStackEntry?.destination?.route != Screen.PermissionsScreen.route && permissionsMap.any { it.value != PermissionStatus.Granted }) {
                                navController.navigate(Screen.PermissionsScreen.route)
                            }
                        }

                        else -> {}
                    }
                }
            }


        }


    }

    @SuppressLint("InlinedApi")
    private fun updatePermissions(permissionMap: SnapshotStateMap<String, PermissionStatus>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissionMap[Manifest.permission.MANAGE_EXTERNAL_STORAGE] =
                if (Environment.isExternalStorageManager()) PermissionStatus.Granted else PermissionStatus.Denied
        } else {
            permissionMap[Manifest.permission.WRITE_EXTERNAL_STORAGE] =
                checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionMap[Manifest.permission.POST_NOTIFICATIONS] =
                checkPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionMap[Manifest.permission.POST_NOTIFICATIONS] = PermissionStatus.Granted
        }
        Timber.d("Checking Permissions")
        permissionMap.forEach { Timber.d("Permission: ${it.key} is ${it.value}") }
    }

    private fun checkPermission(permission: String): PermissionStatus {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isFirstTimeAsked = prefs.getBoolean(permission, true)
        val rationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        return when {
            !hasPermission && !isFirstTimeAsked && !rationale -> PermissionStatus.DeniedForever
            !hasPermission -> PermissionStatus.Denied
            hasPermission -> PermissionStatus.Granted
            else -> PermissionStatus.Unset
        }
    }

    private fun startService() {
        if (!serviceStarted) {
            serviceStarted = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                baseContext?.startForegroundService(Intent(baseContext, KuromeService::class.java))
            } else {
                baseContext?.startService(Intent(baseContext, KuromeService::class.java))
            }
        }
    }
}
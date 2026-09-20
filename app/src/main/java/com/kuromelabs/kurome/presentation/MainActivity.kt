package com.kuromelabs.kurome.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
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
import com.kuromelabs.kurome.presentation.util.Route
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var serviceStarted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val permissionsMap = mutableStateMapOf<String, PermissionStatus>()
        updatePermissions(permissionsMap)

        setContent {
            KuromeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val startDestination: Route =
                        if (permissionsMap.any { it.value != PermissionStatus.Granted }) Route.Permissions
                        else Route.Devices

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable<Route.Permissions> {
                            PermissionScreen(permissionsMap)
                        }
                        composable<Route.Devices> {
                            // The foreground service owns discovery, so it starts as soon as
                            // the user reaches the device list with permissions in hand.
                            LaunchedEffect(Unit) { startService() }
                            DevicesScreen(
                                onDeviceClick = { device ->
                                    navController.navigate(
                                        Route.DeviceDetail(device.id, device.name)
                                    )
                                },
                                onAddDeviceClick = { navController.navigate(Route.AddDevice) },
                            )
                        }
                        composable<Route.DeviceDetail> {
                            DeviceDetailsScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable<Route.AddDevice> {
                            AddDeviceScreen(onBackClick = { navController.popBackStack() })
                        }
                    }

                    RecheckPermissionsOnResume(navController, permissionsMap)
                }
            }
        }
    }

    /**
     * The storage permission is granted in Settings rather than in a dialog, so its state can
     * change while the app is backgrounded. Re-read it on every resume and bounce back to the
     * permission screen if something was revoked.
     */
    @Composable
    private fun RecheckPermissionsOnResume(
        navController: NavController,
        permissionsMap: SnapshotStateMap<String, PermissionStatus>,
    ) {
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                updatePermissions(permissionsMap)
                val missing = permissionsMap.any { it.value != PermissionStatus.Granted }
                val alreadyThere = navController.currentBackStackEntry
                    ?.destination
                    ?.hasRoute(Route.Permissions::class) == true
                if (missing && !alreadyThere) {
                    navController.navigate(Route.Permissions)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            permissionMap[Manifest.permission.ACCESS_LOCAL_NETWORK] =
                checkPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
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
        if (serviceStarted) return
        serviceStarted = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, KuromeService::class.java))
        } else {
            startService(Intent(this, KuromeService::class.java))
        }
    }
}

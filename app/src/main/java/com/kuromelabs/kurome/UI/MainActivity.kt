package com.kuromelabs.kurome.UI

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.kuromelabs.kurome.BuildConfig
import com.kuromelabs.kurome.KuromeApplication
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.UI.theme.KuromeTheme
import com.kuromelabs.kurome.UI.theme.topAppBar
import com.kuromelabs.kurome.models.Device
import com.kuromelabs.kurome.models.DeviceViewModel
import com.kuromelabs.kurome.models.DeviceViewModelFactory


class MainActivity : ComponentActivity() {
    private val deviceViewModel: DeviceViewModel by viewModels {
        DeviceViewModelFactory((application as KuromeApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KuromeTheme {
                Kurome()
            }
        }
    }

    private fun hasFilePermissions(): Boolean {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) ||
            ContextCompat.checkSelfPermission(
                baseContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) return true
        return false
    }

    @Composable
    private fun Kurome() {
        val lifecycleOwner = LocalLifecycleOwner.current
        var hasFilePermissions by remember { mutableStateOf(hasFilePermissions()) }
        var shouldShowPermissionScreen by remember { mutableStateOf(!hasFilePermissions()) }
        DisposableEffect(key1 = lifecycleOwner, effect = {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME)
                    if (!hasFilePermissions()) {
                        shouldShowPermissionScreen = true
                        hasFilePermissions = false
                    } else
                        hasFilePermissions = true
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        })
        if (shouldShowPermissionScreen) {
            PermissionOnboardScreen(
                hasFilePermissions,
                onDone = { shouldShowPermissionScreen = false })

        } else {
            MainScreen()
        }
    }

    @Composable
    private fun MainScreen() {
        val devices by deviceViewModel.combinedDevices.collectAsState(initial = emptyList())
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Kurome") },
                    backgroundColor = MaterialTheme.colors.topAppBar,
                    navigationIcon = {
                        IconButton(onClick = { /* ... */ }) {
                            Icon(Icons.Filled.Menu, contentDescription = null)
                        }
                    })
            },
        ) { innerPadding ->
            DeviceList(devices, Modifier.padding(innerPadding))
        }
    }

    @Composable
    private fun DeviceRow(device: Device) {
        val resources = LocalContext.current.resources
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,

            ) {

            Image(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                painter = painterResource(R.drawable.ic_baseline_computer_48),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface)
            )
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (device.isPaired) if (device.isConnected()) resources.getString(R.string.status_connected)
                    else resources.getString(R.string.status_disconnected)
                    else resources.getString(R.string.status_available),
                    style = MaterialTheme.typography.subtitle2
                )
            }
        }
    }

    @Composable
    private fun DeviceList(devices: List<Device>, modifier: Modifier) {
        LazyColumn(modifier = modifier) {
            items(devices) { device ->
                DeviceRow(device)
            }
        }
    }

    @Preview(uiMode = UI_MODE_NIGHT_YES, showBackground = true, name = "DeviceListPreviewDark")
    @Preview(showBackground = true)
    @Composable
    private fun DeviceListPreview(
        devices: List<Device> = listOf(
            Device("Device Preview 1", "testId", ""), Device("Device Preview 2", "testId", "")
        )
    ) {
        KuromeTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                LazyColumn {
                    items(devices) { device ->
                        DeviceRow(device)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    private fun PermissionOnboardScreen(hasFilePermissions: Boolean, onDone: () -> Unit) {
        val permissionsState =
            rememberPermissionState(permission = Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val resources = LocalContext.current.resources
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 60.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = resources.getString(R.string.permissions_title),
                    style = MaterialTheme.typography.h3
                )
                Text(
                    text = resources.getString(R.string.permissions_body),
                    style = MaterialTheme.typography.subtitle1
                )
                //if android is R or higher, show MANAGE_EXTERNAL_STORAGE card, otherwise show
                //WRITE_EXTERNAL_STORAGE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    PermissionRow(
                        resources.getString(R.string.manage_external_storage_title),
                        resources.getString(R.string.manage_external_storage_body),
                        onClick = {
                            val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                            startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
                            )
                        }, hasFilePermissions
                    )
                } else {
                    val permanentlyDenied =
                        !permissionsState.status.shouldShowRationale && !permissionsState.status.isGranted
                    PermissionRow(
                        resources.getString(R.string.write_external_storage_title),
                        if (permanentlyDenied)
                            resources.getString(R.string.write_external_storage_denied_permanently)
                        else
                            resources.getString(R.string.write_external_storage_body),
                        onClick = {
                            permissionsState.launchPermissionRequest()
                        }, hasFilePermissions
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 20.dp, bottom = 8.dp),
                    enabled = hasFilePermissions
                ) {
                    Text(resources.getString(R.string.done))
                }
            }
        }
    }

    @Composable
    private fun PermissionRow(
        permissionTitle: String,
        permissionBody: String,
        onClick: (() -> Unit),
        hasPermission: Boolean
    ) {
        val resources = LocalContext.current.resources
        Surface(
            modifier = Modifier
                .padding(top = 20.dp)
                .border(BorderStroke(2.dp, MaterialTheme.colors.primary), RoundedCornerShape(5.dp))
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(0.7f)) {
                    Text(
                        text = permissionTitle,
                        style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.ExtraBold)
                    )
                    Text(
                        text = permissionBody,
                        style = MaterialTheme.typography.body2,
                    )
                }
                TextButton(
                    modifier = Modifier.weight(0.3f),
                    onClick = onClick,
                    enabled = !hasPermission
                ) {
                    Text(
                        text = if (hasPermission) resources.getString(R.string.granted_permission)
                        else resources.getString(R.string.grant_permission),
                        style = MaterialTheme.typography.button
                    )
                }
            }
        }
    }

    @Preview(
        uiMode = UI_MODE_NIGHT_YES,
        showBackground = true,
        name = "PermissionOnboardPreviewDark"
    )
    @Preview(showBackground = true)
    @Composable
    private fun PermissionOnboardPreview() {
        KuromeTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                PermissionOnboardScreen(false, onDone = {})
            }
        }
    }

}
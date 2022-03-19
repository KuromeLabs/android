package com.kuromelabs.kurome.presentation.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.kuromelabs.kurome.BuildConfig
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.presentation.getFilePermissionEvent
import com.kuromelabs.kurome.presentation.permissions.components.PermissionRow
import com.kuromelabs.kurome.presentation.util.Screen

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(
    navController: NavController,
    viewModel: PermissionViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val context = LocalContext.current
    val resources = context.resources
    DisposableEffect(key1 = lifecycleOwner, effect = {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME)
                viewModel.onEvent(getFilePermissionEvent(context))
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    })
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
            val storage = viewModel.storagePermissionState.value
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                PermissionRow(
                    resources.getString(R.string.manage_external_storage_title),
                    resources.getString(R.string.manage_external_storage_body),
                    onClick = {
                        val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                        startActivity(
                            context,Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
                        ,null)
                    }, storage
                )
            } else {
                val permissionsState =
                    rememberPermissionState(permission = Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
                    }, storage
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { navController.navigate(Screen.DevicesScreen.route) },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 20.dp, bottom = 8.dp),
                enabled = storage
            ) {
                Text(resources.getString(R.string.done))
            }
        }
    }
}



package com.kuromelabs.kurome.presentation.permissions.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.kuromelabs.kurome.BuildConfig
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.presentation.permissions.PermissionViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(
    onDone: () -> Unit,
    viewModel: PermissionViewModel
) {
    val permissionsState =
        rememberPermissionState(permission = Manifest.permission.WRITE_EXTERNAL_STORAGE)
    val context = LocalContext.current
    val resources = context.resources
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
                onClick = onDone,
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

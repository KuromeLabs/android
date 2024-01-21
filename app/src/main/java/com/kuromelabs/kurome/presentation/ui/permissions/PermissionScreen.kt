package com.kuromelabs.kurome.presentation.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.preference.PreferenceManager
import com.kuromelabs.kurome.BuildConfig
import com.kuromelabs.kurome.R

@Composable
fun PermissionScreen(
    permissionsMap: SnapshotStateMap<String, PermissionStatus>
) {
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
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                text = resources.getString(R.string.permissions_body),
                style = MaterialTheme.typography.titleMedium
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
                            context,
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri),
                            null
                        )
                    }, permissionsMap[Manifest.permission.MANAGE_EXTERNAL_STORAGE]!!
                )
            } else {
                WritePermissionItem(permissionsMap = permissionsMap)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                NotificationPermissionItem(permissionsMap)
            }
        }
    }
}

fun handlePermissionResult(
    context: Context,
    permission: String,
    isGranted: Boolean,
    permissionsMap: SnapshotStateMap<String, PermissionStatus>
) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    if (isGranted) {
        permissionsMap[permission] = PermissionStatus.Granted
    } else {
        val isFirstTimeAsked = prefs.getBoolean(permission, true)
        val rationale =
            ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, permission)
        if (!isFirstTimeAsked && !rationale) {
            permissionsMap[permission] = PermissionStatus.DeniedForever
        } else {
            permissionsMap[permission] = PermissionStatus.Denied
        }
        prefs.edit().putBoolean(permission, false).apply()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun NotificationPermissionItem(permissionsMap: SnapshotStateMap<String, PermissionStatus>) {
    val context = LocalContext.current
    val resources = context.resources

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        handlePermissionResult(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
            isGranted,
            permissionsMap
        )
    }

    PermissionRow(
        permissionTitle = resources.getString(R.string.notification_permission_title),
        permissionBody = if (permissionsMap[Manifest.permission.POST_NOTIFICATIONS]!! == PermissionStatus.DeniedForever)
            resources.getString(R.string.notification_permission_denied_permanently)
        else
            resources.getString(R.string.notification_permission_body),
        onClick = {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        permissionStatus = permissionsMap[Manifest.permission.POST_NOTIFICATIONS]!!,
    )
}

@Composable
fun WritePermissionItem(permissionsMap: SnapshotStateMap<String, PermissionStatus>) {
    val context = LocalContext.current
    val resources = context.resources

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        handlePermissionResult(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            isGranted,
            permissionsMap
        )
    }

    PermissionRow(
        permissionTitle = resources.getString(R.string.write_external_storage_title),
        permissionBody = if (permissionsMap[Manifest.permission.WRITE_EXTERNAL_STORAGE]!! == PermissionStatus.DeniedForever)
            resources.getString(R.string.write_external_storage_denied_permanently)
        else
            resources.getString(R.string.write_external_storage_body),
        onClick = {
            launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        },
        permissionStatus = permissionsMap[Manifest.permission.WRITE_EXTERNAL_STORAGE]!!,
    )

}


@Composable
fun PermissionRow(
    permissionTitle: String,
    permissionBody: String,
    onClick: (() -> Unit),
    permissionStatus: PermissionStatus
) {
    val resources = LocalContext.current.resources
    Surface(
        modifier = Modifier
            .padding(top = 20.dp)
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(5.dp))
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(0.7f)) {
                Text(
                    text = permissionTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    text = permissionBody,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(
                modifier = Modifier.weight(0.3f),
                onClick = onClick,
                enabled = (permissionStatus != PermissionStatus.DeniedForever && permissionStatus != PermissionStatus.Granted)
            ) {
                Text(
                    text = if (permissionStatus == PermissionStatus.Granted) resources.getString(R.string.granted_permission)
                    else resources.getString(R.string.grant_permission),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

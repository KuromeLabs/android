package com.kuromelabs.kurome.presentation.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.application.repository.DeviceContext
import com.kuromelabs.kurome.presentation.util.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    viewModel: DeviceViewModel = hiltViewModel(),
    navController: NavController
) {
    val devices = viewModel.connectedDevices.collectAsState().value
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Kurome") },
                navigationIcon = {
                    IconButton(onClick = { /* ... */ }) {
                        Icon(Icons.Filled.Menu, contentDescription = null)
                    }
                })
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            items(devices) { deviceContext ->
                DeviceRow(deviceContext, Modifier.clickable {
                    navController.navigate("${Screen.DeviceDetailScreen.route}/${deviceContext.device.id}")
                })
            }
        }
    }

}

@Composable
fun DeviceRow(context: DeviceContext, modifier: Modifier) {
    val resources = LocalContext.current.resources
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,

        ) {

        Icon(
            imageVector = Icons.Filled.Computer,
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 16.dp).size(48.dp),
        )
        Column {
            Text(
                text = context.device.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = when (context.state) {
                    DeviceContext.State.CONNECTED_TRUSTED -> resources.getString(R.string.status_connected)
                    DeviceContext.State.DISCONNECTED -> resources.getString(R.string.status_disconnected)
                    DeviceContext.State.CONNECTED_UNTRUSTED -> resources.getString(R.string.status_available)
                    DeviceContext.State.CONNECTING -> resources.getString(R.string.status_connecting)
                    else -> "Unknown"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
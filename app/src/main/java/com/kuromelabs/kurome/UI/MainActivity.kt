package com.kuromelabs.kurome.UI

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
                MainScreen()
            }
        }
    }

    @Composable
    private fun MainScreen() {
        val devices by deviceViewModel.combinedDevices.collectAsState(initial = emptyList())
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Kurome") },
                    backgroundColor = MaterialTheme.colors.topAppBar,
                    navigationIcon = {
                        IconButton(onClick = { /* ... */ }) {
                            Icon(Icons.Filled.Menu, contentDescription = null)
                        }
                    }
                )
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
                    text = if (device.isPaired)
                        if (device.isConnected())
                            resources.getString(R.string.status_connected)
                        else
                            resources.getString(R.string.status_disconnected)
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
            Device("Device Preview 1", "testId", ""),
            Device("Device Preview 2", "testId", "")
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

}
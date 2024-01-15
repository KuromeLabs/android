package com.kuromelabs.kurome.presentation.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.kuromelabs.kurome.presentation.devices.components.DeviceRow
import com.kuromelabs.kurome.presentation.util.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    viewModel: DeviceViewModel,
    modifier: Modifier,
    navController: NavController
) {
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
        LazyColumn(modifier = modifier.padding(innerPadding)) {
            items(viewModel.connectedDevices.value) { deviceContext ->
                DeviceRow(deviceContext, Modifier.clickable {
                    viewModel.setSelectedDevice(deviceContext)
                    navController.navigate(Screen.DeviceDetailScreen.route)
                })
            }
        }
    }

}
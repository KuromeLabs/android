package com.kuromelabs.kurome.presentation.devices.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kuromelabs.kurome.UI.theme.topAppBar
import com.kuromelabs.kurome.presentation.devices.DeviceViewModel

@Composable
fun DevicesScreen(viewModel: DeviceViewModel, modifier: Modifier) {
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
        LazyColumn(modifier = modifier.padding(innerPadding)) {
            items(viewModel.state.value.devices) { device ->
                DeviceRow(device)
            }
        }
    }

}
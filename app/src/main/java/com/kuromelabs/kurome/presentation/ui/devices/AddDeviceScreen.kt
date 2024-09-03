package com.kuromelabs.kurome.presentation.ui.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(navController: NavController, viewModel: AddDeviceViewModel = hiltViewModel()) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Kurome") },
                    navigationIcon = {
                        IconButton(onClick = {
                            navController.popBackStack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    })
            },
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                var text by remember { mutableStateOf("") }

                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Label") }
                )
                Button(onClick = {
                    viewModel.manuallyConnectDevice(text)
                }) {
                    Text("Connect")
                }

            }
        }
}
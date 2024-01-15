package com.kuromelabs.kurome.presentation.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import timber.log.Timber

@Composable
fun DevicesDetailScreen(
    viewModel: DeviceViewModel,
    modifier: Modifier,
) {
    Scaffold(
        topBar = {/** TODO **/ }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(padding)
                .background(MaterialTheme.colorScheme.inverseOnSurface)
        ) {
             }
            Timber.d("Selected device: ${viewModel.selectedDevice.value.device.name}")
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    text = viewModel.selectedDevice.value.device.name,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        }

    }




package com.kuromelabs.kurome.presentation.devices.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.presentation.devices.DeviceState

@Composable
fun DeviceRow(state: DeviceState) {
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
                text = state.device.name,
                style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = if (state.isPaired) if (state.isConnected) resources.getString(R.string.status_connected)
                else resources.getString(R.string.status_disconnected)
                else resources.getString(R.string.status_available),
                style = MaterialTheme.typography.subtitle2
            )
        }
    }
}
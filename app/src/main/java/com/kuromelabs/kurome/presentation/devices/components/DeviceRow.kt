package com.kuromelabs.kurome.presentation.devices.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.application.repository.DeviceContext

@Composable
fun DeviceRow(context: DeviceContext, modifier: Modifier) {
    val resources = LocalContext.current.resources
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,

        ) {

        Image(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            painter = painterResource(R.drawable.ic_baseline_computer_48),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),

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
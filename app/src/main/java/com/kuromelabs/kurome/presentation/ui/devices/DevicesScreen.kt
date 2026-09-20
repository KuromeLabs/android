package com.kuromelabs.kurome.presentation.ui.devices

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.infrastructure.device.DeviceState
import com.kuromelabs.kurome.infrastructure.device.PairStatus
import com.kuromelabs.kurome.presentation.ui.theme.KuromeTheme

@Composable
fun DevicesScreen(
    onDeviceClick: (DeviceState) -> Unit,
    onAddDeviceClick: () -> Unit,
    viewModel: DeviceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DevicesContent(
        uiState = uiState,
        onDeviceClick = onDeviceClick,
        onAddDeviceClick = onAddDeviceClick,
    )
}

@Composable
fun DevicesContent(
    uiState: DevicesUiState,
    onDeviceClick: (DeviceState) -> Unit,
    onAddDeviceClick: () -> Unit,
) {
    // With nothing in the list there is no list to title, and a collapsing header has
    // nothing to collapse against, so the empty state gets the whole screen instead.
    if (uiState.isEmpty) {
        DevicesEmpty(onAddDeviceClick = onAddDeviceClick)
    } else {
        DevicesList(
            uiState = uiState,
            onDeviceClick = onDeviceClick,
            onAddDeviceClick = onAddDeviceClick,
        )
    }
}

@Composable
private fun DevicesEmpty(onAddDeviceClick: () -> Unit) {
    Scaffold(
        floatingActionButton = { AddDeviceFab(onClick = onAddDeviceClick) },
    ) { innerPadding ->
        SearchingForDevices(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicesList(
    uiState: DevicesUiState,
    onDeviceClick: (DeviceState) -> Unit,
    onAddDeviceClick: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.devices_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = { AddDeviceFab(onClick = onAddDeviceClick) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding(),
                // Enough room to scroll the last card clear of the FAB.
                bottom = innerPadding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            deviceSection(
                titleRes = R.string.devices_section_paired,
                devices = uiState.paired,
                onDeviceClick = onDeviceClick,
            )
            deviceSection(
                titleRes = R.string.devices_section_available,
                devices = uiState.available,
                onDeviceClick = onDeviceClick,
            )
        }
    }
}

@Composable
private fun AddDeviceFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.add_24dp),
                contentDescription = null,
            )
        },
        text = { Text(stringResource(R.string.devices_add)) },
    )
}

private fun LazyListScope.deviceSection(
    @StringRes titleRes: Int,
    devices: List<DeviceState>,
    onDeviceClick: (DeviceState) -> Unit,
) {
    if (devices.isEmpty()) return

    item(key = "header-$titleRes") {
        SectionHeader(
            title = stringResource(titleRes),
            modifier = Modifier.animateItem(),
        )
    }
    items(devices, key = { it.id }) { device ->
        DeviceCard(
            state = device,
            onClick = { onDeviceClick(device) },
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun DeviceCard(
    state: DeviceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = state.status

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DeviceAvatar(status = status)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                StatusLine(status = status)
            }
        }
    }
}

/** Round tinted badge holding the device glyph; the tint carries the connection state. */
@Composable
private fun DeviceAvatar(status: DeviceStatus, size: Int = 44) {
    Surface(
        shape = CircleShape,
        color = status.containerColor(),
        contentColor = status.onContainerColor(),
        modifier = Modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.computer_48dp),
                contentDescription = null,
                modifier = Modifier.size((size / 2).dp),
            )
        }
    }
}

/** Status text preceded by a colour-coded dot. */
@Composable
private fun StatusLine(status: DeviceStatus, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = status.accentColor(),
            modifier = Modifier.size(8.dp),
            content = {},
        )
        Text(
            text = stringResource(status.label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Shown until the first peer is heard. The slow pulse is there to say "still listening"
 * without a spinner implying a request that could time out.
 */
@Composable
private fun SearchingForDevices(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "radar")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    Box(modifier = modifier.padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // Expanding ring that fades as it grows.
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(96.dp)
                        .scale(0.6f + pulse * 0.9f)
                        .graphicsLayer { alpha = (1f - pulse) * 0.25f },
                    content = {},
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(88.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.wifi_tethering_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.devices_empty_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.devices_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DevicesContentPreview() {
    KuromeTheme {
        DevicesContent(
            uiState = DevicesUiState(
                paired = listOf(
                    DeviceState("Workstation", "1", PairStatus.PAIRED, connected = true),
                    DeviceState("Old laptop", "2", PairStatus.PAIRED, connected = false),
                ),
                available = listOf(
                    DeviceState("DESKTOP-4F2A", "3", PairStatus.UNPAIRED, connected = true),
                    DeviceState("Living room PC", "4", PairStatus.PAIR_REQUESTED, connected = true),
                ),
            ),
            onDeviceClick = {},
            onAddDeviceClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DevicesEmptyPreview() {
    KuromeTheme {
        DevicesContent(
            uiState = DevicesUiState(),
            onDeviceClick = {},
            onAddDeviceClick = {},
        )
    }
}

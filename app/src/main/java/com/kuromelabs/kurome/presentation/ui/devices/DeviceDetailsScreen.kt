package com.kuromelabs.kurome.presentation.ui.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import android.content.ClipData
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
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
import kotlinx.coroutines.launch

@Composable
fun DeviceDetailsScreen(
    onBackClick: () -> Unit,
    viewModel: DeviceDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DeviceDetailsContent(
        state = state,
        onBackClick = onBackClick,
        onPairClick = viewModel::pairDevice,
        onForgetClick = viewModel::forgetDevice,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsContent(
    state: DeviceState,
    onBackClick: () -> Unit,
    onPairClick: () -> Unit,
    onForgetClick: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showForgetDialog by remember { mutableStateOf(false) }

    val idCopiedMessage = stringResource(R.string.device_details_id_copied)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.arrow_back_24dp),
                            contentDescription = stringResource(R.string.device_details_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            DeviceHero(state = state)
            Spacer(Modifier.height(24.dp))
            PairingActions(
                state = state,
                onPairClick = onPairClick,
                onForgetClick = { showForgetDialog = true },
            )
            Spacer(Modifier.height(24.dp))
            DetailsCard(
                state = state,
                onCopyId = {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText(state.name, state.id))
                        )
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            snackbarHostState.showSnackbar(idCopiedMessage)
                        }
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showForgetDialog) {
        ForgetDeviceDialog(
            deviceName = state.name,
            onDismiss = { showForgetDialog = false },
            onConfirm = {
                showForgetDialog = false
                onForgetClick()
                onBackClick()
            },
        )
    }
}


@Composable
private fun DeviceHero(state: DeviceState) {
    val status = state.status

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = status.containerColor(),
            contentColor = status.onContainerColor(),
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.computer_48dp),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = state.name,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        StatusPill(status = status)
    }
}

@Composable
private fun StatusPill(status: DeviceStatus) {
    Surface(
        shape = CircleShape,
        color = status.containerColor(),
        contentColor = status.onContainerColor(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (status == DeviceStatus.Pending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    // Inherit the pill's content colour instead of the default primary,
                    // which would clash with the tertiary container behind it.
                    color = LocalContentColor.current,
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = status.accentColor(),
                    modifier = Modifier.size(8.dp),
                    content = {},
                )
            }
            Text(
                text = stringResource(status.label),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun PairingActions(
    state: DeviceState,
    onPairClick: () -> Unit,
    onForgetClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state.pairStatus) {
            PairStatus.PAIRED -> {
                OutlinedButton(
                    onClick = onForgetClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.link_off_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.device_details_forget))
                }
            }

            PairStatus.PAIR_REQUESTED -> {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.device_details_pairing))
                }
            }

            PairStatus.UNPAIRED, PairStatus.PAIR_REQUESTED_BY_PEER -> {
                Button(
                    onClick = onPairClick,
                    enabled = state.connected,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.add_link_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.device_details_pair))
                }
            }
        }

        // Explains the disabled Pair button rather than leaving it silently dead.
        AnimatedVisibility(visible = !state.connected && state.pairStatus != PairStatus.PAIRED) {
            Text(
                text = stringResource(R.string.device_details_offline_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun DetailsCard(state: DeviceState, onCopyId: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            DetailRow(
                label = stringResource(R.string.device_details_connection),
                value = stringResource(state.status.label),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            DetailRow(
                label = stringResource(R.string.device_details_trust),
                value = stringResource(
                    if (state.pairStatus == PairStatus.PAIRED) R.string.device_details_trusted
                    else R.string.device_details_untrusted
                ),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            DetailRow(
                label = stringResource(R.string.device_details_id),
                value = state.id,
                trailing = {
                    IconButton(onClick = onCopyId) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.content_copy_24dp),
                            contentDescription = stringResource(R.string.device_details_copy_id),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = if (trailing == null) 16.dp else 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun ForgetDeviceDialog(
    deviceName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.link_off_24dp),
                contentDescription = null,
            )
        },
        title = { Text(stringResource(R.string.forget_dialog_title)) },
        text = { Text(stringResource(R.string.forget_dialog_body, deviceName)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.forget_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun DeviceDetailsPairedPreview() {
    KuromeTheme {
        DeviceDetailsContent(
            state = DeviceState(
                name = "Workstation",
                id = "8f14e45f-ea8f-4b3d-9c2a-1b7c0d5e6f7a",
                pairStatus = PairStatus.PAIRED,
                connected = true,
            ),
            onBackClick = {},
            onPairClick = {},
            onForgetClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DeviceDetailsUnpairedPreview() {
    KuromeTheme {
        DeviceDetailsContent(
            state = DeviceState(
                name = "DESKTOP-4F2A",
                id = "3c59dc04-8e88-4650-a2a6-f7a1b2c3d4e5",
                pairStatus = PairStatus.UNPAIRED,
                connected = true,
            ),
            onBackClick = {},
            onPairClick = {},
            onForgetClick = {},
        )
    }
}

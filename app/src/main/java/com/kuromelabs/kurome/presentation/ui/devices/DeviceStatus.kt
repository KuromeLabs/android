package com.kuromelabs.kurome.presentation.ui.devices

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.infrastructure.device.DeviceState
import com.kuromelabs.kurome.infrastructure.device.PairStatus

/**
 * The four states worth telling the user apart, collapsed from the
 * [PairStatus] x connected matrix that the UI actually receives.
 */
enum class DeviceStatus(@param:StringRes val label: Int) {
    /** Paired and reachable: the only state where the peer can touch the filesystem. */
    Connected(R.string.status_connected),

    /** Paired but not on the network right now. */
    Offline(R.string.status_disconnected),

    /** Neither paired nor reachable: nothing can be done with it until it shows up. */
    Unavailable(R.string.status_unavailable),

    /** Reachable but untrusted, so pairing is the obvious next step. */
    Available(R.string.status_available),

    /** A pair request is in flight in one direction or the other. */
    Pending(R.string.status_pair_requested),

    /** The peer asked us first, so the wording is different from [Pending]. */
    PendingFromPeer(R.string.status_pair_requested_by_peer),
}

val DeviceState.status: DeviceStatus
    get() = when {
        !connected && pairStatus == PairStatus.PAIRED -> DeviceStatus.Offline
        !connected -> DeviceStatus.Unavailable
        pairStatus == PairStatus.PAIRED -> DeviceStatus.Connected
        pairStatus == PairStatus.PAIR_REQUESTED -> DeviceStatus.Pending
        pairStatus == PairStatus.PAIR_REQUESTED_BY_PEER -> DeviceStatus.PendingFromPeer
        else -> DeviceStatus.Available
    }

/** Colour of the small status dot, and the tint of the device avatar. */
@Composable
@ReadOnlyComposable
fun DeviceStatus.accentColor(): Color = when (this) {
    DeviceStatus.Connected -> MaterialTheme.colorScheme.primary
    DeviceStatus.Available -> MaterialTheme.colorScheme.tertiary
    DeviceStatus.Pending, DeviceStatus.PendingFromPeer -> MaterialTheme.colorScheme.tertiary
    DeviceStatus.Offline, DeviceStatus.Unavailable -> MaterialTheme.colorScheme.outline
}

@Composable
@ReadOnlyComposable
fun DeviceStatus.containerColor(): Color = when (this) {
    DeviceStatus.Connected -> MaterialTheme.colorScheme.primaryContainer
    DeviceStatus.Available,
    DeviceStatus.Pending,
    DeviceStatus.PendingFromPeer -> MaterialTheme.colorScheme.tertiaryContainer
    DeviceStatus.Offline, DeviceStatus.Unavailable -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
@ReadOnlyComposable
fun DeviceStatus.onContainerColor(): Color = when (this) {
    DeviceStatus.Connected -> MaterialTheme.colorScheme.onPrimaryContainer
    DeviceStatus.Available,
    DeviceStatus.Pending,
    DeviceStatus.PendingFromPeer -> MaterialTheme.colorScheme.onTertiaryContainer
    DeviceStatus.Offline, DeviceStatus.Unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
}

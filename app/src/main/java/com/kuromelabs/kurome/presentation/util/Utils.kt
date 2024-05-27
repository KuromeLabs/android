package com.kuromelabs.kurome.presentation.util

import android.content.Context
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.infrastructure.device.PairStatus

class Utils {
    companion object {
        fun GetStatusMessage(status: PairStatus, connected: Boolean, context: Context): String {
            return when (status) {
                PairStatus.PAIRED -> if (connected) context.resources.getString(R.string.status_connected) else context.resources.getString(R.string.status_disconnected)
                PairStatus.UNPAIRED -> if (connected) "Available (Tap to pair)" else context.resources.getString(R.string.status_disconnected)
                PairStatus.PAIR_REQUESTED -> if (connected) "Pair requested" else context.resources.getString(R.string.status_disconnected)
                PairStatus.PAIR_REQUESTED_BY_PEER -> if (connected) "Pair requested by peer" else context.resources.getString(R.string.status_disconnected)
            }
        }
    }
}
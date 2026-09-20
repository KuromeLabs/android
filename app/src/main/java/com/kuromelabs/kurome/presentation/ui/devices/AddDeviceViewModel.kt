package com.kuromelabs.kurome.presentation.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuromelabs.kurome.infrastructure.device.DeviceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddDeviceViewModel @Inject constructor(
    private val deviceService: DeviceService
) : ViewModel() {

    private val _attempts = Channel<String>(Channel.BUFFERED)

    /** One event per connection attempt, so the screen can confirm the tap landed. */
    val attempts: Flow<String> = _attempts.receiveAsFlow()

    fun manuallyConnectDevice(ip: String) {
        val address = ip.trim()
        if (address.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            deviceService.connectManually(ip = address, port = MANUAL_CONNECT_PORT)
        }
        _attempts.trySend(address)
    }

    private companion object {
        /** The port the Windows client listens on for TCP. */
        const val MANUAL_CONNECT_PORT = 33587
    }
}

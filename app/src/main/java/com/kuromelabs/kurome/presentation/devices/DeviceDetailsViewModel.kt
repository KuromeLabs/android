package com.kuromelabs.kurome.presentation.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuromelabs.kurome.application.repository.DeviceContext
import com.kuromelabs.kurome.application.use_case.device.DeviceUseCases
import com.kuromelabs.kurome.domain.Device
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DeviceDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    deviceUseCases: DeviceUseCases
) : ViewModel() {
    private var deviceId: String = savedStateHandle["deviceId"]!!
    private val connectedDevices = deviceUseCases.getConnectedDevices()
    private val savedDevices = deviceUseCases.getSavedDevices()

    var deviceContext: StateFlow<DeviceContext> = combine(connectedDevices, savedDevices) { connectedDevices, savedDevices ->
        val connectedDeviceIds = connectedDevices.map { it.device.id }
        val saved = savedDevices.filter { !connectedDeviceIds.contains(it.device.id) }
        connectedDevices.find { it.device.id == deviceId } ?:
        saved.find { it.device.id == deviceId } ?: DeviceContext(Device("null", "Unknown Device"), DeviceContext.State.DISCONNECTED)
    }
        .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        initialValue = DeviceContext(Device(deviceId, "Unknown Device"),
            DeviceContext.State.DISCONNECTED
        )
    )
}


package com.kuromelabs.kurome.presentation.ui.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuromelabs.kurome.application.devices.Device
import com.kuromelabs.kurome.application.use_case.device.DeviceUseCases
import com.kuromelabs.kurome.infrastructure.device.DeviceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DeviceDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deviceUseCases: DeviceUseCases
) : ViewModel() {
    private var deviceId: String = savedStateHandle["deviceId"]!!
    private val connectedDevices = deviceUseCases.getConnectedDevices()
    private val savedDevices = deviceUseCases.getSavedDevices()

    var deviceContext: StateFlow<DeviceState> = combine(connectedDevices, savedDevices) { connectedDevices, savedDevices ->
        val connectedDeviceIds = connectedDevices.map { it.device.id }
        val saved = savedDevices.filter { !connectedDeviceIds.contains(it.id) }
        when (deviceId) {
            in connectedDeviceIds -> {
                connectedDevices.find { it.device.id == deviceId }!!
            }
            in saved.map { it.id } -> {
                DeviceState(saved.find { it.id == deviceId }!!, DeviceState.Status.PAIRED, null).apply { isConnected = false }
            }
            else -> {
                DeviceState(Device("null", "Unknown Device"), DeviceState.Status.UNPAIRED, null)
            }
        }
    }
        .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
            initialValue = DeviceState(Device("null", "Unknown Device"), DeviceState.Status.UNPAIRED, null)
    )

    fun pairDevice(device: Device) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {deviceUseCases.pairDevice(device) }
        }
    }
}


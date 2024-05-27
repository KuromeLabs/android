package com.kuromelabs.kurome.presentation.ui.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuromelabs.kurome.application.use_case.device.DeviceUseCases
import com.kuromelabs.kurome.infrastructure.device.DeviceState
import com.kuromelabs.kurome.infrastructure.device.PairStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DeviceDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deviceUseCases: DeviceUseCases
) : ViewModel() {
    private var deviceId: String = savedStateHandle["deviceId"]!!

    // This name is only used when the device is not paired, so it does not change
    private val temporaryDeviceName: String = savedStateHandle["deviceName"]!!
    private val connectedDevices = deviceUseCases.getConnectedDevices()
    private val savedDevices = deviceUseCases.getSavedDevices()

    var deviceContext: SharedFlow<DeviceState?> =
        combine(connectedDevices, savedDevices) { connectedDevices, savedDevices ->
            val connectedDeviceIds = connectedDevices.map { it.id }
            val saved = savedDevices.filter { !connectedDeviceIds.contains(it.id) }
            when (deviceId) {
                in connectedDeviceIds -> {
                    connectedDevices.find { it.id == deviceId }
                }
                in saved.map {it.id} -> {
                    val device = saved.find { it.id == deviceId }
                    DeviceState(device!!.name, device.id, PairStatus.PAIRED, false)
                }
                else -> DeviceState(temporaryDeviceName, deviceId, PairStatus.UNPAIRED, false)
            }

        }.filterNotNull()
            .shareIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                1
            )

    fun pairDevice(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { deviceUseCases.pairDevice(id) }
        }
    }
}


package com.kuromelabs.kurome.presentation.devices

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuromelabs.kurome.application.repository.DeviceContext
import com.kuromelabs.kurome.application.use_case.device.DeviceUseCases
import com.kuromelabs.kurome.domain.Device
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val deviceUseCases: DeviceUseCases
) : ViewModel() {
    private var _connectedDevices = mutableStateOf(emptyList<DeviceContext>())
    var connectedDevices: State<List<DeviceContext>> = _connectedDevices
    private var getDevicesJob: Job? = null

    private var _selectedDevice = mutableStateOf(DeviceContext(Device("", "v"), DeviceContext.State.DISCONNECTED))
    var selectedDevice: State<DeviceContext> = _selectedDevice

    init {
        getDevices()
    }

    fun onEvent(event: DevicesEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            when (event) {
                is DevicesEvent.PairDevice -> deviceUseCases.pairDevice(event.device)
                is DevicesEvent.UnpairDevice -> deviceUseCases.unpairDevice(event.device)
                is DevicesEvent.GetDevices -> getDevices()
            }
        }
    }

    private fun getDevices() {
        getDevicesJob?.cancel()
        getDevicesJob = deviceUseCases.getConnectedDevices()
            .onEach { _connectedDevices.value = it }
            .launchIn(viewModelScope)
    }

    fun setSelectedDevice(deviceContext: DeviceContext) {
        Timber.d("Selected device: ${deviceContext.device.name}")
        _selectedDevice.value = deviceContext
    }

    fun clearSelectedDevice() {
        _selectedDevice.value = DeviceContext(Device("", "v"), DeviceContext.State.DISCONNECTED)
    }
}
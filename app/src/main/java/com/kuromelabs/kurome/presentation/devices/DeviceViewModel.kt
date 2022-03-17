package com.kuromelabs.kurome.presentation.devices

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuromelabs.kurome.domain.use_case.DeviceUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val deviceUseCases: DeviceUseCases
) : ViewModel() {
    init {
        getDevices()
    }

    private val _state = mutableStateOf(DevicesState())
    val state: State<DevicesState> = _state

    private var getDevicesJob: Job? = null


    suspend fun onEvent(event: DevicesEvent) {
        when (event) {
            is DevicesEvent.PairDevice -> deviceUseCases.pairDevice(event.device)
            is DevicesEvent.UnpairDevice -> deviceUseCases.unpairDevice(event.device)
            is DevicesEvent.GetDevices -> getDevices()
        }
    }

    private fun getDevices() {
        getDevicesJob?.cancel()
        getDevicesJob = deviceUseCases.getDevices().onEach { deviceList ->
            _state.value = _state.value.copy(devices = deviceList.map { DeviceState(it ,it.isConnected(), it.isPaired) })
        }.launchIn(viewModelScope)
    }
}
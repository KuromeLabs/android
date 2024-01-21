package com.kuromelabs.kurome.presentation.ui.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuromelabs.kurome.application.use_case.device.DeviceUseCases
import com.kuromelabs.kurome.infrastructure.device.DeviceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val deviceUseCases: DeviceUseCases,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    var connectedDevices: StateFlow<List<DeviceState>> = deviceUseCases.getConnectedDevices()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = emptyList())

    val allDevices: StateFlow<List<DeviceState>> = combine(
        connectedDevices,
        deviceUseCases.getSavedDevices()
    ) { connectedDevices, savedDevices ->
        val connectedDeviceIds = connectedDevices.map { it.device.id }
        val saved = savedDevices.filter { !connectedDeviceIds.contains(it.device.id) }
        connectedDevices + saved


    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initialValue = emptyList())

}
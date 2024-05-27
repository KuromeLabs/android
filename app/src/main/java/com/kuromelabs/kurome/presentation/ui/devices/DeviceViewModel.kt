package com.kuromelabs.kurome.presentation.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuromelabs.kurome.application.use_case.device.DeviceUseCases
import com.kuromelabs.kurome.infrastructure.device.DeviceState
import com.kuromelabs.kurome.infrastructure.device.PairStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    deviceUseCases: DeviceUseCases
) : ViewModel() {

    private var connectedDevices: StateFlow<List<DeviceState>> = deviceUseCases.getConnectedDevices()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = emptyList())

    val allDevices: StateFlow<List<DeviceState>> = combine(
        connectedDevices,
        deviceUseCases.getSavedDevices()
    ) { connectedDevices, savedDevices ->
        val connectedDeviceIds = connectedDevices.map { it.id }
        val saved = savedDevices
            .filter { !connectedDeviceIds.contains(it.id) }
            .map { DeviceState(it.name, it.id, PairStatus.PAIRED, false) }
        connectedDevices + saved


    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initialValue = emptyList())

}
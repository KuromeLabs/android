package com.kuromelabs.kurome.presentation.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuromelabs.kurome.application.devices.DeviceRepository
import com.kuromelabs.kurome.infrastructure.device.DeviceService
import com.kuromelabs.kurome.infrastructure.device.DeviceState
import com.kuromelabs.kurome.infrastructure.device.PairStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    deviceRepository: DeviceRepository,
    deviceService: DeviceService
) : ViewModel() {

    private var connectedDevices = deviceService.deviceStates.transform { emit(it.values) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initialValue = emptyList())

    val allDevices: StateFlow<List<DeviceState>> = combine(
        connectedDevices,
        deviceRepository.getSavedDevices()
    ) { connectedDevices, savedDevices ->
        val connectedDeviceIds = connectedDevices.map { it.id }
        val saved = savedDevices
            .filter { !connectedDeviceIds.contains(it.id) }
            .map { DeviceState(it.name, it.id, PairStatus.PAIRED, false) }
        connectedDevices + saved


    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initialValue = emptyList())

}
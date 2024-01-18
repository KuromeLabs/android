package com.kuromelabs.kurome.presentation.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuromelabs.kurome.application.repository.DeviceContext
import com.kuromelabs.kurome.application.use_case.device.DeviceUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val deviceUseCases: DeviceUseCases,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    var connectedDevices: StateFlow<List<DeviceContext>> = deviceUseCases.getConnectedDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initialValue = emptyList())

    val allDevices: StateFlow<List<DeviceContext>> = combine(
        connectedDevices,
        deviceUseCases.getSavedDevices()
    ) { connectedDevices, savedDevices ->
        val connectedDeviceIds = connectedDevices.map { it.device.id }
        val saved = savedDevices.filter { !connectedDeviceIds.contains(it.device.id) }
        connectedDevices + saved


    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initialValue = emptyList())

}
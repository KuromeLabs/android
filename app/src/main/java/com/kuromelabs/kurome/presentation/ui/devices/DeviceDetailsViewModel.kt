package com.kuromelabs.kurome.presentation.ui.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kuromelabs.kurome.application.devices.DeviceRepository
import com.kuromelabs.kurome.infrastructure.device.DeviceService
import com.kuromelabs.kurome.infrastructure.device.DeviceState
import com.kuromelabs.kurome.infrastructure.device.PairStatus
import com.kuromelabs.kurome.presentation.util.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DeviceDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deviceService: DeviceService,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.DeviceDetail>()
    val deviceId: String = route.deviceId

    private val fallbackName: String = route.deviceName

    val uiState: StateFlow<DeviceState> = combine(
        deviceService.deviceStates,
        deviceRepository.getSavedDevices()
    ) { connectedById, savedDevices ->
        connectedById[deviceId]
            ?: savedDevices.firstOrNull { it.id == deviceId }
                ?.let { DeviceState(it.name, it.id, PairStatus.PAIRED, connected = false) }
            ?: DeviceState(fallbackName, deviceId, PairStatus.UNPAIRED, connected = false)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DeviceState(fallbackName, deviceId, PairStatus.UNPAIRED, connected = false)
    )

    fun pairDevice() {
        deviceService.sendOutgoingPairRequest(deviceId)
    }

    fun forgetDevice() {
        deviceService.unpairDevice(deviceId)
    }
}

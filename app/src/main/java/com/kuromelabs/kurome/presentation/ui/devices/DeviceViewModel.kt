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
import javax.inject.Inject

data class DevicesUiState(
    val paired: List<DeviceState> = emptyList(),
    val available: List<DeviceState> = emptyList(),
) {
    val isEmpty: Boolean get() = paired.isEmpty() && available.isEmpty()
}

@HiltViewModel
class DeviceViewModel @Inject constructor(
    deviceRepository: DeviceRepository,
    deviceService: DeviceService
) : ViewModel() {

    val uiState: StateFlow<DevicesUiState> = combine(
        deviceService.deviceStates,
        deviceRepository.getSavedDevices()
    ) { connectedById, savedDevices ->
        val connected = connectedById.values
        val connectedIds = connectedById.keys

        // A saved device with no live handle is paired but offline.
        val offlinePaired = savedDevices
            .filter { it.id !in connectedIds }
            .map { DeviceState(it.name, it.id, PairStatus.PAIRED, connected = false) }

        val all = connected + offlinePaired
        DevicesUiState(
            paired = all.filter { it.pairStatus == PairStatus.PAIRED }.sortedForDisplay(),
            available = all.filter { it.pairStatus != PairStatus.PAIRED }.sortedForDisplay(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesUiState())

    private fun Iterable<DeviceState>.sortedForDisplay() =
        sortedWith(compareByDescending<DeviceState> { it.connected }.thenBy { it.name.lowercase() })
}

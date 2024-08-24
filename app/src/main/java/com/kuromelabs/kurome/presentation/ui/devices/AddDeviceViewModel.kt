package com.kuromelabs.kurome.presentation.ui.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuromelabs.kurome.infrastructure.device.DeviceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddDeviceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deviceService: DeviceService
) : ViewModel() {

    fun manuallyConnectDevice(ip: String){
        viewModelScope.launch(Dispatchers.IO) {
            deviceService.handleUdp("ManuallyConnectDevice", "ManuallyConnectedDeviceId", ip, 33587)
        }
    }
}
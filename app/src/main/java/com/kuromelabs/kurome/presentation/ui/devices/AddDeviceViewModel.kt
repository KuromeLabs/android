package com.kuromelabs.kurome.presentation.ui.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.kuromelabs.kurome.application.use_case.device.DeviceUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AddDeviceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    val deviceUseCases: DeviceUseCases
) : ViewModel() {

    fun manuallyConnectDevice(ip: String){
        deviceUseCases.manuallyConnectDevice(ip)
    }
}
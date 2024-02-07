package com.kuromelabs.kurome.presentation.ui.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuromelabs.kurome.application.use_case.device.DeviceUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddDeviceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    val deviceUseCases: DeviceUseCases
) : ViewModel() {

    fun manuallyConnectDevice(ip: String){
        viewModelScope.launch(Dispatchers.IO) {
            deviceUseCases.manuallyConnectDevice(ip)
        }
    }
}
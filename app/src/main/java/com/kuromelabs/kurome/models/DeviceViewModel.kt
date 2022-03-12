package com.kuromelabs.kurome.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuromelabs.kurome.database.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(private val repository: DeviceRepository) : ViewModel() {
    val combinedDevices = repository.serviceDevices

    fun insert(device: Device) = viewModelScope.launch {
        repository.insert(device)
    }
}
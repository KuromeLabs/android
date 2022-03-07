package com.kuromelabs.kurome.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kuromelabs.kurome.database.DeviceRepository
import kotlinx.coroutines.launch

class DeviceViewModel(private val repository: DeviceRepository) : ViewModel() {
    val combinedDevices = repository.serviceDevices

    fun insert(device: Device) = viewModelScope.launch {
        repository.insert(device)
    }
}
class DeviceViewModelFactory(private val repository: DeviceRepository) : ViewModelProvider.Factory{
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeviceViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return DeviceViewModel(repository) as T
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
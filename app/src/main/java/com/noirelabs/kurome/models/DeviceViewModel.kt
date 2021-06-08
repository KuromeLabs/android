package com.noirelabs.kurome.models

import androidx.lifecycle.*
import com.noirelabs.kurome.database.DeviceRepository
import kotlinx.coroutines.launch

class DeviceViewModel(private val repository: DeviceRepository) : ViewModel() {
    val allDevices: LiveData<List<Device>> = repository.allDevices.asLiveData()
    fun insert(device: Device) = viewModelScope.launch {
        repository.insert(device)
    }
}
class DeviceViewModelFactory(private val repository: DeviceRepository) : ViewModelProvider.Factory{
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeviceViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return DeviceViewModel(repository) as T
        throw IllegalArgumentException("Unknown ViewModel class")
    }

}
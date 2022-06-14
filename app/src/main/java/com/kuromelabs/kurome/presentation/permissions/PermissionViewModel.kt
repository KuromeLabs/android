package com.kuromelabs.kurome.presentation.permissions

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PermissionViewModel @Inject constructor(
) : ViewModel() {

    private val _storagePermissionState = mutableStateOf(false)
    val storagePermissionState: State<Boolean> = _storagePermissionState

    fun onEvent(event: PermissionEvent) {
        when (event) {
            is PermissionEvent.Granted -> {
                _storagePermissionState.value = true
            }
            is PermissionEvent.Revoked -> {
                _storagePermissionState.value = false
            }
        }
    }

}
package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.storage.StorageMode
import com.example.core.storage.StorageModeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val storageModeManager = StorageModeManager(application)

    private val _storageMode = MutableStateFlow<StorageMode?>(null)
    val storageMode: StateFlow<StorageMode?> = _storageMode.asStateFlow()

    private var pendingMode: StorageMode? = null

    init {
        viewModelScope.launch {
            storageModeManager.storageMode.collect { stored ->
                val pending = pendingMode
                if (pending != null) {
                    if (stored == pending) {
                        pendingMode = null
                    }
                    return@collect
                }
                _storageMode.value = stored
            }
        }
    }

    fun setStorageMode(mode: StorageMode) {
        pendingMode = mode
        _storageMode.value = mode
        viewModelScope.launch {
            storageModeManager.setStorageMode(mode)
        }
    }
}

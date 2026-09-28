package com.memorycapture.app.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppStartupState {
    data object Loading : AppStartupState
    data object Ready : AppStartupState
}

class AppStartupViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<AppStartupState>(AppStartupState.Loading)
    val state: StateFlow<AppStartupState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            delay(750)
            mutableState.value = AppStartupState.Ready
        }
    }
}

package com.memorycapture.app.ui.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlayerUiModeStore {
    private val mutablePip = MutableStateFlow(false)
    val inPictureInPicture: StateFlow<Boolean> = mutablePip.asStateFlow()

    fun setPictureInPicture(enabled: Boolean) {
        mutablePip.value = enabled
    }
}

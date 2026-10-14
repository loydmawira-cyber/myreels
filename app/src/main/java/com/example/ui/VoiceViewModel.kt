package com.example.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VoiceUiState(
    val selectedVoice: String = "onyx",
    val speakingStyle: String = "narrative"
)

class VoiceViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    fun selectVoice(voiceId: String) {
        _uiState.update { it.copy(selectedVoice = voiceId) }
    }

    fun selectSpeakingStyle(style: String) {
        _uiState.update { it.copy(speakingStyle = style) }
    }
}

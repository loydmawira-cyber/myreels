package com.example.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NicheUiState(
    val selectedNiche: String = "scary_stories",
    val customTopic: String = ""
)

class NicheViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NicheUiState())
    val uiState: StateFlow<NicheUiState> = _uiState.asStateFlow()

    fun selectNiche(niche: String) {
        _uiState.update { it.copy(selectedNiche = niche) }
    }

    fun updateCustomTopic(topic: String) {
        _uiState.update { it.copy(customTopic = topic) }
    }
}

package com.authvex.balaxysefactura.ui.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.repository.AiAssistantRepository
import kotlinx.coroutines.launch

data class AssistantCardState(
    val suggestions: List<String>
)

class HomeViewModel(
    private val aiAssistantRepository: AiAssistantRepository
) : ViewModel() {

    var assistantCard by mutableStateOf<AssistantCardState?>(null)
        private set

    init {
        checkAssistantAvailability()
    }

    fun checkAssistantAvailability() {
        viewModelScope.launch {
            if (aiAssistantRepository.canUseAssistant()) {
                val fallbackSuggestions = listOf("¿Cómo viene el mes?", "¿Quién me debe más?")
                val suggestionsRes = aiAssistantRepository.getSuggestions()
                val loadedSuggestions = suggestionsRes.getOrNull()?.take(2)
                val finalSuggestions = if (!loadedSuggestions.isNullOrEmpty()) loadedSuggestions else fallbackSuggestions

                assistantCard = AssistantCardState(suggestions = finalSuggestions)
            } else {
                assistantCard = null
            }
        }
    }
}

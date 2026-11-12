package com.authvex.balaxysefactura.ui.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.repository.AiAssistantRepository
import kotlinx.coroutines.launch

/**
 * Tarjeta del Asistente en Home. Sin sugerencias: pedirlas al servidor genera
 * preguntas con IA (tokens y varios segundos de espera) en cada carga de Home.
 */
data object AssistantCardState

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
            assistantCard = if (aiAssistantRepository.canUseAssistant()) AssistantCardState else null
        }
    }
}

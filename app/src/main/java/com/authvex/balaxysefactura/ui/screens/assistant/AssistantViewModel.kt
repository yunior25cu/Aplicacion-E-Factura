package com.authvex.balaxysefactura.ui.screens.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.auth.AuthPreferences
import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.AiAssistantRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class ChatRole { USER, ASSISTANT }
enum class MessageStatus { SENT, PENDING_ERROR }

data class ChatMessageUi(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val text: String,
    val status: MessageStatus = MessageStatus.SENT,
    val errorText: String? = null,
    val requestId: String? = null
)

class AssistantViewModel(
    private val repository: AiAssistantRepository,
    private val authPreferences: AuthPreferences,
    private val initialMessage: String? = null
) : ViewModel() {

    var messages by mutableStateOf<List<ChatMessageUi>>(emptyList())
        private set

    var isWaiting by mutableStateOf(false)
        private set

    var isSlow by mutableStateOf(false)
        private set

    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set

    var input by mutableStateOf("")

    private var initialMessageSent = false
    private var slowTimerJob: Job? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val suggestionsRes = repository.getSuggestions()
            suggestions = suggestionsRes.getOrNull() ?: listOf("¿Cómo viene el mes?", "¿Quién me debe más?")

            if (!initialMessageSent && !initialMessage.isNullOrBlank()) {
                initialMessageSent = true
                send(initialMessage)
            }
        }
    }

    fun onInputChanged(newInput: String) {
        if (newInput.length <= 10000) {
            input = newInput
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isWaiting) return

        val userMessage = ChatMessageUi(
            role = ChatRole.USER,
            text = trimmed,
            status = MessageStatus.SENT
        )

        messages = messages + userMessage
        input = ""
        isWaiting = true
        isSlow = false

        startSlowTimer()

        viewModelScope.launch {
            executeSendMessage(userMessage)
        }
    }

    fun retry(messageId: String) {
        if (isWaiting) return
        val targetIndex = messages.indexOfFirst { it.id == messageId }
        if (targetIndex < 0) return

        val targetMessage = messages[targetIndex]
        // Reset status to SENT (remove error)
        val updatedMessage = targetMessage.copy(status = MessageStatus.SENT, errorText = null)
        messages = messages.toMutableList().apply { set(targetIndex, updatedMessage) }

        isWaiting = true
        isSlow = false
        startSlowTimer()

        viewModelScope.launch {
            executeSendMessage(updatedMessage)
        }
    }

    private fun startSlowTimer() {
        slowTimerJob?.cancel()
        slowTimerJob = viewModelScope.launch {
            delay(15000)
            if (isWaiting) {
                isSlow = true
            }
        }
    }

    private suspend fun executeSendMessage(userMessage: ChatMessageUi) {
        try {
            val conversationId = authPreferences.getAiConversationIdSync()
            val result = repository.sendMessage(userMessage.text, conversationId)

            result.onSuccess { response ->
                authPreferences.saveAiConversationId(response.conversationId)
                val assistantMsg = ChatMessageUi(
                    role = ChatRole.ASSISTANT,
                    text = response.response,
                    status = MessageStatus.SENT,
                    requestId = response.requestId
                )
                messages = messages + assistantMsg
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                val displayMsg = when (appError) {
                    is AppError.ServerError -> {
                        if (appError.code == 503) {
                            appError.detail?.takeIf { it.isNotBlank() } ?: "El asistente no está disponible en este momento."
                        } else {
                            "El asistente no pudo responder. Probá de nuevo."
                        }
                    }
                    is AppError.Timeout -> "El asistente tardó demasiado. Probá de nuevo o reformulá la pregunta."
                    is AppError.Forbidden -> "Tu usuario no tiene acceso al Asistente."
                    else -> appError.getDisplayMessage()
                }

                val msgIndex = messages.indexOfFirst { it.id == userMessage.id }
                if (msgIndex >= 0) {
                    val errorMsg = userMessage.copy(
                        status = MessageStatus.PENDING_ERROR,
                        errorText = displayMsg
                    )
                    messages = messages.toMutableList().apply { set(msgIndex, errorMsg) }
                }
            }
        } finally {
            slowTimerJob?.cancel()
            isWaiting = false
            isSlow = false
        }
    }

    fun newConversation() {
        viewModelScope.launch {
            messages = emptyList()
            authPreferences.clearAiConversationId()
            input = ""
        }
    }
}

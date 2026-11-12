package com.authvex.balaxysefactura.ui.screens.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.auth.AuthPreferences
import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.AiAssistantRepository
import com.authvex.balaxysefactura.ui.screens.assistant.cards.ActionExecutionState
import com.authvex.balaxysefactura.ui.screens.assistant.cards.AiConfirmAction
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
    val requestId: String? = null,
    val isFromHistory: Boolean = false,
    val tarjetas: List<AiTarjeta> = emptyList()
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

    var isLoadingHistory by mutableStateOf(false)
        private set

    var historyError by mutableStateOf<String?>(null)
        private set

    var input by mutableStateOf("")

    var actionStates by mutableStateOf<Map<String, ActionExecutionState>>(emptyMap())
        private set

    private var initialMessageSent = false
    private var slowTimerJob: Job? = null
    private var currentGen = 0

    init {
        loadInitialData()
    }

    // Sin sugerencias del servidor: se generan con IA (tokens y segundos de espera).
    private fun loadInitialData() {
        if (!initialMessage.isNullOrBlank() && !initialMessageSent) {
            initialMessageSent = true
            send(initialMessage)
        }
        loadHistoryIfAvailable()
    }

    private fun loadHistoryIfAvailable() {
        viewModelScope.launch {
            val savedConvId = authPreferences.getAiConversationIdSync()
            if (!savedConvId.isNullOrBlank()) {
                isLoadingHistory = true
                historyError = null

                val res = repository.getMessages(savedConvId)
                res.onSuccess { historyList ->
                    if (historyList.isEmpty()) {
                        authPreferences.clearAiConversationId()
                    } else {
                        val mappedHistory = historyList.map { msg ->
                            val isUser = msg.role.equals("user", ignoreCase = true)
                            ChatMessageUi(
                                role = if (isUser) ChatRole.USER else ChatRole.ASSISTANT,
                                text = msg.content,
                                status = MessageStatus.SENT,
                                isFromHistory = true
                            )
                        }
                        val currentNonHistory = messages.filter { !it.isFromHistory }
                        messages = mappedHistory + currentNonHistory
                    }
                }.onFailure {
                    historyError = "No se pudo cargar la conversación anterior. Podés seguir escribiendo o empezar una nueva."
                }
                isLoadingHistory = false
            }
        }
    }

    fun onInputChanged(newInput: String) {
        if (newInput.length <= 10000) {
            input = newInput
        }
    }

    fun getActionExecutionState(actionKey: String): ActionExecutionState {
        return actionStates[actionKey] ?: ActionExecutionState.Pending()
    }

    fun executeCardAction(actionKey: String, action: AiConfirmAction) {
        val currentState = getActionExecutionState(actionKey)
        if (currentState !is ActionExecutionState.Pending) return

        actionStates = actionStates + (actionKey to ActionExecutionState.Executing)

        viewModelScope.launch {
            val result = repository.executeAction(action.herramienta, action.argumentos)
            result.onSuccess { outcome ->
                val nextState = if (outcome.ok) {
                    ActionExecutionState.DoneSuccess(outcome.mensaje)
                } else {
                    ActionExecutionState.DoneError(outcome.mensaje)
                }
                actionStates = actionStates + (actionKey to nextState)
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                val errMsg = appError.getDisplayMessage()
                actionStates = actionStates + (actionKey to ActionExecutionState.Pending(errMsg))
            }
        }
    }

    fun cancelCardAction(actionKey: String) {
        actionStates = actionStates + (actionKey to ActionExecutionState.Cancelled)
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

        val capturedGen = currentGen
        viewModelScope.launch {
            executeSendMessage(userMessage, capturedGen)
        }
    }

    fun retry(messageId: String) {
        if (isWaiting) return
        val targetIndex = messages.indexOfFirst { it.id == messageId }
        if (targetIndex < 0) return

        val targetMessage = messages[targetIndex]
        val updatedMessage = targetMessage.copy(status = MessageStatus.SENT, errorText = null)
        messages = messages.toMutableList().apply { set(targetIndex, updatedMessage) }

        isWaiting = true
        isSlow = false
        startSlowTimer()

        val capturedGen = currentGen
        viewModelScope.launch {
            executeSendMessage(updatedMessage, capturedGen)
        }
    }

    private fun startSlowTimer() {
        slowTimerJob?.cancel()
        slowTimerJob = viewModelScope.launch {
            delay(5000)
            if (isWaiting) {
                isSlow = true
            }
        }
    }

    private suspend fun executeSendMessage(userMessage: ChatMessageUi, gen: Int) {
        try {
            val conversationId = authPreferences.getAiConversationIdSync()
            val result = repository.sendMessage(userMessage.text, conversationId)

            if (gen != currentGen) {
                return
            }

            result.onSuccess { response ->
                authPreferences.saveAiConversationId(response.conversationId)
                val assistantMsg = ChatMessageUi(
                    role = ChatRole.ASSISTANT,
                    text = response.response,
                    status = MessageStatus.SENT,
                    requestId = response.requestId,
                    tarjetas = response.tarjetas ?: emptyList()
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
            if (gen == currentGen) {
                slowTimerJob?.cancel()
                isWaiting = false
                isSlow = false
            }
        }
    }

    fun newConversation() {
        viewModelScope.launch {
            currentGen++
            slowTimerJob?.cancel()
            isWaiting = false
            isSlow = false
            messages = emptyList()
            historyError = null
            authPreferences.clearAiConversationId()
            input = ""
        }
    }
}

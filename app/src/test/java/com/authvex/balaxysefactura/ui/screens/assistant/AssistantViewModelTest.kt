package com.authvex.balaxysefactura.ui.screens.assistant

import com.authvex.balaxysefactura.core.auth.AuthPreferences
import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.AiAssistantRepository
import com.authvex.balaxysefactura.ui.screens.assistant.cards.ActionExecutionState
import com.authvex.balaxysefactura.ui.screens.assistant.cards.AiConfirmAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonElement
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FakeAiAssistantRepository(
    enabled: Boolean = true,
    canUse: Boolean = true
) : AiAssistantRepository(mock(), mock(), enabled) {

    var nextResponse: Result<AiChatResponse>? = null
    var historyResult: Result<List<AiConversationMessage>>? = null
    var conversationsResult: Result<List<AiConversationSummary>>? = null
    var feedbackResult: Result<Unit>? = null
    var suggestionsResult: Result<List<String>>? = null
    var actionResult: Result<AiAccionResultado>? = null
    var suggestionsCallCount = 0
    var actionCount = 0
    var historyCallCount = 0
    var conversationsCallCount = 0
    var feedbackCallCount = 0
    var lastFeedbackRequestId: String? = null
    var lastFeedbackPositive: Boolean? = null
    var lastFeedbackComment: String? = null
    var lastMessageSent: String? = null
    var lastConversationIdSent: String? = null
    var sendCount = 0
    var delayMs: Long = 0

    override suspend fun sendMessage(message: String, conversationId: String?): Result<AiChatResponse> {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return Result.failure(AppError.Validation("El mensaje no puede estar vacío"))
        if (trimmed.length > 10000) return Result.failure(AppError.Validation("El mensaje no puede superar los 10.000 caracteres"))

        sendCount++
        lastMessageSent = trimmed
        lastConversationIdSent = conversationId

        if (delayMs > 0) {
            kotlinx.coroutines.delay(delayMs)
        }

        return nextResponse ?: Result.success(AiChatResponse("Respuesta fake", "c-fake", "r-fake"))
    }

    override suspend fun executeAction(herramienta: String, argumentos: JsonElement?): Result<AiAccionResultado> {
        actionCount++
        if (delayMs > 0) {
            kotlinx.coroutines.delay(delayMs)
        }
        return actionResult ?: Result.success(AiAccionResultado(ok = true, mensaje = "Acción ejecutada"))
    }

    override suspend fun getSuggestions(): Result<List<String>> {
        suggestionsCallCount++
        return suggestionsResult ?: Result.success(listOf("Sugerencia 1", "Sugerencia 2"))
    }

    override suspend fun getMessages(conversationId: String): Result<List<AiConversationMessage>> {
        historyCallCount++
        return historyResult ?: Result.success(emptyList())
    }

    override suspend fun getConversations(): Result<List<AiConversationSummary>> {
        conversationsCallCount++
        return conversationsResult ?: Result.success(emptyList())
    }

    override suspend fun sendFeedback(requestId: String, positive: Boolean, comment: String?): Result<Unit> {
        feedbackCallCount++
        lastFeedbackRequestId = requestId
        lastFeedbackPositive = positive
        lastFeedbackComment = comment
        if (delayMs > 0) {
            kotlinx.coroutines.delay(delayMs)
        }
        return feedbackResult ?: Result.success(Unit)
    }

    override suspend fun canUseAssistant(): Boolean = true
}

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

    private lateinit var fakeRepository: FakeAiAssistantRepository
    private lateinit var authPreferences: AuthPreferences
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAiAssistantRepository()
        authPreferences = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful send adds user and assistant bubbles with cards and saves conversationId`() = runTest(testScheduler) {
        val tarjeta = AiTarjeta(tool = "deuda_vencida", titulo = "Deuda")
        fakeRepository.nextResponse = Result.success(
            AiChatResponse(
                response = "Respuesta del asistente",
                conversationId = "c-100",
                requestId = "r-200",
                tarjetas = listOf(tarjeta)
            )
        )

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        viewModel.send("Hola")

        assertEquals(2, viewModel.messages.size)

        val userMsg = viewModel.messages[0]
        assertEquals(ChatRole.USER, userMsg.role)
        assertEquals("Hola", userMsg.text)

        val assistantMsg = viewModel.messages[1]
        assertEquals(ChatRole.ASSISTANT, assistantMsg.role)
        assertEquals("Respuesta del asistente", assistantMsg.text)
        assertEquals(1, assistantMsg.tarjetas.size)
        assertEquals("deuda_vencida", assistantMsg.tarjetas[0].tool)

        verify(authPreferences).saveAiConversationId("c-100")
        assertFalse(viewModel.isWaiting)
    }

    @Test
    fun `saved conversationId loads and maps history messages`() = runTest(testScheduler) {
        whenever(authPreferences.getAiConversationIdSync()).thenReturn("c-history")
        fakeRepository.historyResult = Result.success(
            listOf(
                AiConversationMessage("user", "Pregunta previa"),
                AiConversationMessage("assistant", "Respuesta previa")
            )
        )

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        assertEquals(2, viewModel.messages.size)
        assertTrue(viewModel.messages[0].isFromHistory)
        assertEquals(ChatRole.USER, viewModel.messages[0].role)
        assertEquals("Pregunta previa", viewModel.messages[0].text)

        assertTrue(viewModel.messages[1].isFromHistory)
        assertEquals(ChatRole.ASSISTANT, viewModel.messages[1].role)
        assertEquals("Respuesta previa", viewModel.messages[1].text)
        assertFalse(viewModel.isLoadingHistory)
        assertNull(viewModel.historyError)
    }

    @Test
    fun `empty history list clears saved conversationId`() = runTest(testScheduler) {
        whenever(authPreferences.getAiConversationIdSync()).thenReturn("c-deleted")
        fakeRepository.historyResult = Result.success(emptyList())

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        assertEquals(0, viewModel.messages.size)
        verify(authPreferences).clearAiConversationId()
        assertFalse(viewModel.isLoadingHistory)
        assertNull(viewModel.historyError)
    }

    @Test
    fun `history loading error keeps conversationId and sets non-blocking historyError`() = runTest(testScheduler) {
        whenever(authPreferences.getAiConversationIdSync()).thenReturn("c-error")
        fakeRepository.historyResult = Result.failure(AppError.ServerError(503, "No disponible"))

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        assertEquals(0, viewModel.messages.size)
        verify(authPreferences, never()).clearAiConversationId()
        assertFalse(viewModel.isLoadingHistory)
        assertNotNull(viewModel.historyError)
        assertTrue(viewModel.historyError!!.contains("No se pudo cargar la conversación anterior"))
    }

    @Test
    fun `cannot send message while isWaiting`() = runTest(testScheduler) {
        fakeRepository.delayMs = 5000L

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        viewModel.send("Primer mensaje")

        assertTrue(viewModel.isWaiting)
        val initialSize = viewModel.messages.size

        viewModel.send("Segundo mensaje")

        assertEquals(initialSize, viewModel.messages.size)
    }

    @Test
    fun `failed send sets PENDING_ERROR status and retry re-sends message`() = runTest(testScheduler) {
        fakeRepository.nextResponse = Result.failure(AppError.ServerError(503, "Servicio no disponible"))

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        viewModel.send("Hola con error")

        // First attempt failed
        assertEquals(1, viewModel.messages.size)
        val errorMsg = viewModel.messages[0]
        assertEquals(ChatRole.USER, errorMsg.role)
        assertEquals(MessageStatus.PENDING_ERROR, errorMsg.status)
        assertEquals("Servicio no disponible", errorMsg.errorText)

        // Set next response to success and retry
        fakeRepository.nextResponse = Result.success(AiChatResponse("Respuesta recuperada", "c-101", "r-201"))
        viewModel.retry(errorMsg.id)

        // Retry succeeded
        assertEquals(2, viewModel.messages.size)
        assertEquals(MessageStatus.SENT, viewModel.messages[0].status)
        assertEquals(ChatRole.ASSISTANT, viewModel.messages[1].role)
        assertEquals("Respuesta recuperada", viewModel.messages[1].text)
    }

    @Test
    fun `initialMessage is sent without calling getSuggestions`() = runTest(testScheduler) {
        fakeRepository.nextResponse = Result.success(AiChatResponse("Hola desde initial", "c-init", "r-init"))

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = "Pregunta inicial")

        assertEquals(2, viewModel.messages.size)
        assertEquals("Pregunta inicial", viewModel.messages[0].text)
        assertEquals("Hola desde initial", viewModel.messages[1].text)
        assertEquals(1, fakeRepository.sendCount)
        assertEquals(0, fakeRepository.suggestionsCallCount)
    }

    @Test
    fun `opening the chat without a message does not request suggestions`() = runTest(testScheduler) {
        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        assertTrue(viewModel.messages.isEmpty())
        assertEquals(0, fakeRepository.sendCount)
        assertEquals(0, fakeRepository.suggestionsCallCount)
    }

    @Test
    fun `executeCardAction transitions state from executing to success or error`() = runTest(testScheduler) {
        fakeRepository.actionResult = Result.success(AiAccionResultado(ok = true, mensaje = "Factura A-1043 confirmada."))

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)
        val action = AiConfirmAction("confirmar_factura", null)

        viewModel.executeCardAction("msg1_0", action)

        val state = viewModel.getActionExecutionState("msg1_0")
        assertTrue(state is ActionExecutionState.DoneSuccess)
        assertEquals("Factura A-1043 confirmada.", (state as ActionExecutionState.DoneSuccess).message)
        assertEquals(1, fakeRepository.actionCount)
    }

    @Test
    fun `second executeCardAction on same card is ignored while executing`() = runTest(testScheduler) {
        fakeRepository.delayMs = 5000L
        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)
        val action = AiConfirmAction("confirmar_factura", null)

        viewModel.executeCardAction("msg1_0", action)
        assertEquals(ActionExecutionState.Executing, viewModel.getActionExecutionState("msg1_0"))

        viewModel.executeCardAction("msg1_0", action)
        assertEquals(1, fakeRepository.actionCount)
    }

    @Test
    fun `executeCardAction on http error returns to Pending with error message and allows retry`() = runTest(testScheduler) {
        fakeRepository.actionResult = Result.failure(AppError.Forbidden(detail = "El token de confirmación venció o ya fue usado."))

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)
        val action = AiConfirmAction("confirmar_factura", null)

        viewModel.executeCardAction("msg1_0", action)

        val firstState = viewModel.getActionExecutionState("msg1_0")
        assertTrue(firstState is ActionExecutionState.Pending)
        assertEquals("El token de confirmación venció o ya fue usado.", (firstState as ActionExecutionState.Pending).errorMessage)
        assertEquals(1, fakeRepository.actionCount)

        // Second attempt calls repository again and succeeds
        fakeRepository.actionResult = Result.success(AiAccionResultado(ok = true, mensaje = "Factura confirmada."))
        viewModel.executeCardAction("msg1_0", action)

        val secondState = viewModel.getActionExecutionState("msg1_0")
        assertTrue(secondState is ActionExecutionState.DoneSuccess)
        assertEquals("Factura confirmada.", (secondState as ActionExecutionState.DoneSuccess).message)
        assertEquals(2, fakeRepository.actionCount)
    }

    @Test
    fun `executeCardAction on ok false sets DoneError and does not allow retry`() = runTest(testScheduler) {
        fakeRepository.actionResult = Result.success(AiAccionResultado(ok = false, mensaje = "La factura ya estaba confirmada."))

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)
        val action = AiConfirmAction("confirmar_factura", null)

        viewModel.executeCardAction("msg1_0", action)

        val state = viewModel.getActionExecutionState("msg1_0")
        assertTrue(state is ActionExecutionState.DoneError)
        assertEquals("La factura ya estaba confirmada.", (state as ActionExecutionState.DoneError).message)
        assertEquals(1, fakeRepository.actionCount)

        // Attempting to execute again is ignored when in DoneError
        fakeRepository.actionResult = Result.success(AiAccionResultado(ok = true, mensaje = "No debería ejecutarse"))
        viewModel.executeCardAction("msg1_0", action)

        assertEquals(1, fakeRepository.actionCount)
        assertTrue(viewModel.getActionExecutionState("msg1_0") is ActionExecutionState.DoneError)
    }

    @Test
    fun `cancelCardAction sets state to Cancelled without calling repository`() = runTest(testScheduler) {
        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        viewModel.cancelCardAction("msg1_0")

        assertEquals(ActionExecutionState.Cancelled, viewModel.getActionExecutionState("msg1_0"))
        assertEquals(0, fakeRepository.actionCount)
    }

    @Test
    fun `newConversation clears messages and conversationId`() = runTest(testScheduler) {
        fakeRepository.nextResponse = Result.success(AiChatResponse("Respuesta", "c-999", "r-999"))

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)
        viewModel.send("Test")

        assertEquals(2, viewModel.messages.size)

        viewModel.newConversation()

        assertEquals(0, viewModel.messages.size)
        verify(authPreferences).clearAiConversationId()
    }

    @Test
    fun `newConversation resets isWaiting and isSlow when a send is pending and discards old response`() = runTest(testScheduler) {
        fakeRepository.delayMs = 10000L
        fakeRepository.nextResponse = Result.success(AiChatResponse("Respuesta vieja", "c-old", "r-old"))

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        viewModel.send("Mensaje pendiente")
        assertTrue(viewModel.isWaiting)

        viewModel.newConversation()

        assertFalse(viewModel.isWaiting)
        assertFalse(viewModel.isSlow)
        assertEquals(0, viewModel.messages.size)

        fakeRepository.delayMs = 0L
        fakeRepository.nextResponse = Result.success(AiChatResponse("Respuesta nueva", "c-new", "r-new"))

        viewModel.send("Nuevo mensaje")

        advanceTimeBy(10000L)

        assertEquals(2, viewModel.messages.size)
        assertEquals("Nuevo mensaje", viewModel.messages[0].text)
        assertEquals("Respuesta nueva", viewModel.messages[1].text)
        verify(authPreferences, never()).saveAiConversationId("c-old")
        verify(authPreferences).saveAiConversationId("c-new")
    }

    @Test
    fun `response from generation prior to newConversation is discarded`() = runTest(testScheduler) {
        fakeRepository.delayMs = 5000L

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        viewModel.send("Mensaje que tardará")

        viewModel.newConversation()

        advanceTimeBy(5000L)

        assertEquals(0, viewModel.messages.size)
    }

    @Test
    fun `isSlow activates after 5 seconds when waiting`() = runTest(testScheduler) {
        fakeRepository.delayMs = 10000L

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        viewModel.send("Consulta lenta")

        assertTrue(viewModel.isWaiting)
        assertFalse(viewModel.isSlow)

        // Advance virtual time by 5.1 seconds
        advanceTimeBy(5100L)

        assertTrue(viewModel.isSlow)
    }

    @Test
    fun `selectConversation saves id, replaces messages and discards in-flight response`() = runTest(testScheduler) {
        fakeRepository.delayMs = 5000L
        fakeRepository.nextResponse = Result.success(AiChatResponse("Respuesta en vuelo vieja", "c-old", "r-old"))

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)
        viewModel.send("Mensaje en vuelo")
        assertTrue(viewModel.isWaiting)

        fakeRepository.historyResult = Result.success(
            listOf(
                AiConversationMessage("user", "Pregunta conversación 2"),
                AiConversationMessage("assistant", "Respuesta conversación 2")
            )
        )

        viewModel.selectConversation("c-200")

        assertFalse(viewModel.isWaiting)
        assertEquals("c-200", viewModel.currentConversationId)
        verify(authPreferences).saveAiConversationId("c-200")
        assertEquals(2, viewModel.messages.size)
        assertTrue(viewModel.messages.all { it.isFromHistory })
        assertEquals("Pregunta conversación 2", viewModel.messages[0].text)
        assertEquals("Respuesta conversación 2", viewModel.messages[1].text)

        // Advance time so the old in-flight response finishes -> must be discarded
        advanceTimeBy(6000L)
        assertEquals(2, viewModel.messages.size)
        verify(authPreferences, never()).saveAiConversationId("c-old")
    }

    @Test
    fun `selectConversation on current conversation does not reload`() = runTest(testScheduler) {
        whenever(authPreferences.getAiConversationIdSync()).thenReturn("c-actual")
        fakeRepository.historyResult = Result.success(
            listOf(AiConversationMessage("user", "Mensaje inicial"))
        )

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)
        assertEquals(1, fakeRepository.historyCallCount)
        assertEquals("c-actual", viewModel.currentConversationId)

        viewModel.selectConversation("c-actual")

        // No additional call to getMessages or saveAiConversationId
        assertEquals(1, fakeRepository.historyCallCount)
        verify(authPreferences, never()).saveAiConversationId("c-actual")
    }

    @Test
    fun `selectConversation with uppercase id matching lowercase currentConversationId does not call getMessages and preserves messages with requestId and tarjetas`() = runTest(testScheduler) {
        val lowerId = "febb332f-5149-4797-8d15-58309c28a4ba"
        val upperId = "FEBB332F-5149-4797-8D15-58309C28A4BA"
        val tarjeta = AiTarjeta(tool = "deuda_vencida", titulo = "Deuda vencida")

        fakeRepository.nextResponse = Result.success(
            AiChatResponse(
                response = "Respuesta con tarjeta",
                conversationId = lowerId,
                requestId = "req-999",
                tarjetas = listOf(tarjeta)
            )
        )

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)
        viewModel.send("¿Quién me debe?")

        assertEquals(lowerId, viewModel.currentConversationId)
        assertEquals(2, viewModel.messages.size)
        assertEquals(0, fakeRepository.historyCallCount)

        viewModel.selectConversation(upperId)

        assertEquals(0, fakeRepository.historyCallCount)
        assertEquals(lowerId, viewModel.currentConversationId)
        assertEquals(2, viewModel.messages.size)
        val assistantMsg = viewModel.messages[1]
        assertEquals("req-999", assistantMsg.requestId)
        assertEquals(1, assistantMsg.tarjetas.size)
        assertEquals("deuda_vencida", assistantMsg.tarjetas[0].tool)
    }

    @Test
    fun `submitFeedback positive records rating and blocks a second submission`() = runTest(testScheduler) {
        fakeRepository.feedbackResult = Result.success(Unit)
        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        viewModel.submitFeedback(requestId = "req-100", positive = true, comment = null)

        assertEquals(FeedbackRating.POSITIVE, viewModel.getFeedbackRating("req-100"))
        assertEquals(1, fakeRepository.feedbackCallCount)
        assertEquals("req-100", fakeRepository.lastFeedbackRequestId)
        assertEquals(true, fakeRepository.lastFeedbackPositive)

        // Second attempt on the same requestId is ignored
        viewModel.submitFeedback(requestId = "req-100", positive = false, comment = "Cambio de opinión")
        assertEquals(1, fakeRepository.feedbackCallCount)
        assertEquals(FeedbackRating.POSITIVE, viewModel.getFeedbackRating("req-100"))
    }

    @Test
    fun `submitFeedback failure clears rating to re-enable buttons and sets feedbackError`() = runTest(testScheduler) {
        fakeRepository.feedbackResult = Result.failure(AppError.ServerError(503, "No se pudo guardar la calificación."))
        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        viewModel.submitFeedback(requestId = "req-200", positive = false, comment = "Mal cálculo")

        assertNull(viewModel.getFeedbackRating("req-200"))
        assertEquals("No se pudo guardar la calificación.", viewModel.feedbackError)
        assertEquals(1, fakeRepository.feedbackCallCount)

        // Can retry since rating was cleared after failure
        fakeRepository.feedbackResult = Result.success(Unit)
        viewModel.submitFeedback(requestId = "req-200", positive = false, comment = "Mal cálculo")
        assertEquals(FeedbackRating.NEGATIVE, viewModel.getFeedbackRating("req-200"))
        assertEquals(2, fakeRepository.feedbackCallCount)
    }

    @Test
    fun `startNewConversation true with initialMessage clears saved id before sending and does not load history`() = runTest(testScheduler) {
        whenever(authPreferences.getAiConversationIdSync()).thenReturn("c-previa")
        fakeRepository.nextResponse = Result.success(
            AiChatResponse("Estado de factura A-123", "c-nueva", "r-nueva")
        )

        val viewModel = AssistantViewModel(
            repository = fakeRepository,
            authPreferences = authPreferences,
            initialMessage = "Contame el estado de la factura A-123: saldo pendiente, vencimiento y cobros aplicados.",
            startNewConversation = true
        )

        verify(authPreferences).clearAiConversationId()
        assertEquals(0, fakeRepository.historyCallCount)
        assertEquals(1, fakeRepository.sendCount)
        assertNull(fakeRepository.lastConversationIdSent)
        assertEquals("c-nueva", viewModel.currentConversationId)
        assertEquals(2, viewModel.messages.size)
    }
}

package com.authvex.balaxysefactura.ui.screens.assistant

import com.authvex.balaxysefactura.core.auth.AuthPreferences
import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.AiAssistantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    var suggestionsResult: Result<List<String>>? = null
    var suggestionsCallCount = 0
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

    override suspend fun getSuggestions(): Result<List<String>> {
        suggestionsCallCount++
        return suggestionsResult ?: Result.success(listOf("Sugerencia 1", "Sugerencia 2"))
    }

    override suspend fun getMessages(conversationId: String): Result<List<AiConversationMessage>> {
        return historyResult ?: Result.success(emptyList())
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
    fun `successful send adds user and assistant bubbles and saves conversationId`() = runTest(testScheduler) {
        fakeRepository.nextResponse = Result.success(
            AiChatResponse(
                response = "Respuesta del asistente",
                conversationId = "c-100",
                requestId = "r-200"
            )
        )

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        viewModel.send("Hola")

        assertEquals(2, viewModel.messages.size)

        val userMsg = viewModel.messages[0]
        assertEquals(ChatRole.USER, userMsg.role)
        assertEquals("Hola", userMsg.text)
        assertEquals(MessageStatus.SENT, userMsg.status)

        val assistantMsg = viewModel.messages[1]
        assertEquals(ChatRole.ASSISTANT, assistantMsg.role)
        assertEquals("Respuesta del asistente", assistantMsg.text)
        assertEquals("r-200", assistantMsg.requestId)

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

        // User triggers newConversation while waiting for response
        viewModel.newConversation()

        // isWaiting and isSlow are reset immediately
        assertFalse(viewModel.isWaiting)
        assertFalse(viewModel.isSlow)
        assertEquals(0, viewModel.messages.size)

        // Subsequent send works immediately
        fakeRepository.delayMs = 0L
        fakeRepository.nextResponse = Result.success(AiChatResponse("Respuesta nueva", "c-new", "r-new"))

        viewModel.send("Nuevo mensaje")

        // Advance time to complete old pending response and new send
        advanceTimeBy(10000L)

        // Only the new message and its response exist (2 messages, not 3 or old response)
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

        // User triggers newConversation while send is in progress
        viewModel.newConversation()

        // Advance time to let the delayed response complete
        advanceTimeBy(5000L)

        // The old response must be discarded and messages must remain empty
        assertEquals(0, viewModel.messages.size)
    }

    @Test
    fun `isSlow activates after 15 seconds when waiting`() = runTest(testScheduler) {
        fakeRepository.delayMs = 20000L

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        viewModel.send("Consulta lenta")

        assertTrue(viewModel.isWaiting)
        assertFalse(viewModel.isSlow)

        // Advance virtual time by 15.1 seconds
        advanceTimeBy(15100L)

        assertTrue(viewModel.isSlow)
    }
}

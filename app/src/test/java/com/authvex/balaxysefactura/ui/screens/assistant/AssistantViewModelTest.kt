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
import org.mockito.kotlin.verify

class FakeAiAssistantRepository(
    enabled: Boolean = true,
    canUse: Boolean = true
) : AiAssistantRepository(mock(), mock(), enabled) {

    var nextResponse: Result<AiChatResponse>? = null
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
        return Result.success(listOf("Sugerencia 1", "Sugerencia 2"))
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
    fun `cannot send message while isWaiting`() = runTest(testScheduler) {
        fakeRepository.delayMs = 5000L

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = null)

        viewModel.send("Primer mensaje")

        // While first send is processing (isWaiting is true), second send is ignored
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
    fun `initialMessage is sent only once`() = runTest(testScheduler) {
        fakeRepository.nextResponse = Result.success(AiChatResponse("Hola desde initial", "c-init", "r-init"))

        val viewModel = AssistantViewModel(fakeRepository, authPreferences, initialMessage = "Pregunta inicial")

        assertEquals(2, viewModel.messages.size)
        assertEquals("Pregunta inicial", viewModel.messages[0].text)
        assertEquals("Hola desde initial", viewModel.messages[1].text)
        assertEquals(1, fakeRepository.sendCount)
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

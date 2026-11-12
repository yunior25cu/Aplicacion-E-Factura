package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AiAssistantRepositoryTest {

    private lateinit var aiAssistantApi: AiAssistantApi
    private lateinit var accessApi: AccessApi

    @Before
    fun setup() {
        aiAssistantApi = mock()
        accessApi = mock()
    }

    @Test
    fun `sendMessage with empty message fails with validation without calling API`() = runTest {
        val repo = AiAssistantRepository(aiAssistantApi, accessApi, enabled = true)

        val result = repo.sendMessage("   ", null)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is AppError.Validation)
        assertEquals("El mensaje no puede estar vacío", error?.message)
        verify(aiAssistantApi, never()).chat(any())
    }

    @Test
    fun `sendMessage exceeding 10000 characters fails with validation without calling API`() = runTest {
        val repo = AiAssistantRepository(aiAssistantApi, accessApi, enabled = true)
        val longMessage = "a".repeat(10001)

        val result = repo.sendMessage(longMessage, null)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is AppError.Validation)
        assertEquals("El mensaje no puede superar los 10.000 caracteres", error?.message)
        verify(aiAssistantApi, never()).chat(any())
    }

    @Test
    fun `getMessages returns success and fails with ErrorMapper`() = runTest {
        val messagesList = listOf(AiConversationMessage("user", "Hola"))
        whenever(aiAssistantApi.messages("c123")).thenReturn(messagesList)

        val repo = AiAssistantRepository(aiAssistantApi, accessApi, enabled = true)

        val successRes = repo.getMessages("c123")
        assertTrue(successRes.isSuccess)
        assertEquals(1, successRes.getOrNull()?.size)

        val errorResponse = retrofit2.Response.error<List<AiConversationMessage>>(503, "{\"mensaje\":\"Servicio no disponible\"}".toResponseBody())
        whenever(aiAssistantApi.messages("c454")).thenThrow(retrofit2.HttpException(errorResponse))
        val failRes = repo.getMessages("c454")
        assertTrue(failRes.isFailure)
        assertTrue(failRes.exceptionOrNull() is AppError.ServerError)
    }

    @Test
    fun `canUseAssistant returns false if enabled is false`() = runTest {
        val repo = AiAssistantRepository(aiAssistantApi, accessApi, enabled = false)

        val canUse = repo.canUseAssistant()

        assertFalse(canUse)
        verify(accessApi, never()).me()
    }

    @Test
    fun `canUseAssistant returns true when capability present and caches result until invalidated`() = runTest {
        val profile = AccessProfile(capabilities = listOf("efactura.sales", "ai.assistant"))
        whenever(accessApi.me()).thenReturn(profile)

        val repo = AiAssistantRepository(aiAssistantApi, accessApi, enabled = true)

        val canUse1 = repo.canUseAssistant()
        assertTrue(canUse1)

        val canUse2 = repo.canUseAssistant()
        assertTrue(canUse2)

        // API called only once due to caching
        verify(accessApi, times(1)).me()

        // Invalidate access cache
        repo.invalidateAccess()

        val canUse3 = repo.canUseAssistant()
        assertTrue(canUse3)

        // API called a second time after invalidation
        verify(accessApi, times(2)).me()
    }

    @Test
    fun `canUseAssistant returns false on network or api error`() = runTest {
        whenever(accessApi.me()).thenThrow(RuntimeException("Network error"))

        val repo = AiAssistantRepository(aiAssistantApi, accessApi, enabled = true)

        val canUse = repo.canUseAssistant()

        assertFalse(canUse)
    }

    @Test
    fun `sendFeedback truncates comment to 500 chars, sends null when blank, and maps errors`() = runTest {
        whenever(aiAssistantApi.feedback(any())).thenReturn(AiFeedbackResponse(ok = true))
        val repo = AiAssistantRepository(aiAssistantApi, accessApi, enabled = true)

        // 1. Long comment (> 500 chars) is trimmed and truncated to 500 chars
        val longComment = "   " + "x".repeat(600) + "   "
        val res1 = repo.sendFeedback(requestId = "r-1", positive = false, comment = longComment)
        assertTrue(res1.isSuccess)
        verify(aiAssistantApi).feedback(
            AiFeedbackRequest(
                requestId = "r-1",
                rating = "negative",
                comment = "x".repeat(500)
            )
        )

        // 2. Blank comment after trim sends null
        val res2 = repo.sendFeedback(requestId = "r-2", positive = true, comment = "   \n  ")
        assertTrue(res2.isSuccess)
        verify(aiAssistantApi).feedback(
            AiFeedbackRequest(
                requestId = "r-2",
                rating = "positive",
                comment = null
            )
        )

        // 3. HTTP 503 error is mapped via ErrorMapper
        val errorResponse = retrofit2.Response.error<AiFeedbackResponse>(
            503,
            "{\"mensaje\":\"Servicio de feedback no disponible\"}".toResponseBody()
        )
        whenever(aiAssistantApi.feedback(any())).thenThrow(retrofit2.HttpException(errorResponse))

        val resFail = repo.sendFeedback(requestId = "r-3", positive = true, comment = null)
        assertTrue(resFail.isFailure)
        val err = resFail.exceptionOrNull()
        assertTrue(err is AppError.ServerError)
        assertEquals("Servicio de feedback no disponible", (err as AppError.ServerError).getDisplayMessage())
    }
}

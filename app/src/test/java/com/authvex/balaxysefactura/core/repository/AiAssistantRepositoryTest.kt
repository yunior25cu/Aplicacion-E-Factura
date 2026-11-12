package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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
}

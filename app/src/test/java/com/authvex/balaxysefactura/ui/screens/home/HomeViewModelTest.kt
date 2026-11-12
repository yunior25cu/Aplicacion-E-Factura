package com.authvex.balaxysefactura.ui.screens.home

import com.authvex.balaxysefactura.core.repository.AiAssistantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var aiAssistantRepository: AiAssistantRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        aiAssistantRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shows assistant card with suggestions when canUseAssistant is true`() = runTest {
        whenever(aiAssistantRepository.canUseAssistant()).thenReturn(true)
        whenever(aiAssistantRepository.getSuggestions()).thenReturn(Result.success(listOf("¿Sugerencia 1?", "¿Sugerencia 2?", "¿Sugerencia 3?")))

        val viewModel = HomeViewModel(aiAssistantRepository)

        assertNotNull(viewModel.assistantCard)
        assertEquals(2, viewModel.assistantCard?.suggestions?.size)
        assertEquals("¿Sugerencia 1?", viewModel.assistantCard?.suggestions?.get(0))
        assertEquals("¿Sugerencia 2?", viewModel.assistantCard?.suggestions?.get(1))
    }

    @Test
    fun `shows assistant card with fallback suggestions when suggestions api fails`() = runTest {
        whenever(aiAssistantRepository.canUseAssistant()).thenReturn(true)
        whenever(aiAssistantRepository.getSuggestions()).thenReturn(Result.failure(RuntimeException("Error")))

        val viewModel = HomeViewModel(aiAssistantRepository)

        assertNotNull(viewModel.assistantCard)
        assertEquals(2, viewModel.assistantCard?.suggestions?.size)
        assertEquals("¿Cómo viene el mes?", viewModel.assistantCard?.suggestions?.get(0))
        assertEquals("¿Quién me debe más?", viewModel.assistantCard?.suggestions?.get(1))
    }

    @Test
    fun `does not show assistant card when canUseAssistant is false`() = runTest {
        whenever(aiAssistantRepository.canUseAssistant()).thenReturn(false)

        val viewModel = HomeViewModel(aiAssistantRepository)

        assertNull(viewModel.assistantCard)
    }
}

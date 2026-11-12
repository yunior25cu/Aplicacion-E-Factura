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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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
    fun `shows assistant card without requesting suggestions when canUseAssistant is true`() = runTest {
        whenever(aiAssistantRepository.canUseAssistant()).thenReturn(true)

        val viewModel = HomeViewModel(aiAssistantRepository)

        assertEquals(AssistantCardState, viewModel.assistantCard)
        verify(aiAssistantRepository, never()).getSuggestions()
    }

    @Test
    fun `does not show assistant card when canUseAssistant is false`() = runTest {
        whenever(aiAssistantRepository.canUseAssistant()).thenReturn(false)

        val viewModel = HomeViewModel(aiAssistantRepository)

        assertNull(viewModel.assistantCard)
    }
}

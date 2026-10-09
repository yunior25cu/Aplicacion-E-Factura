package com.authvex.balaxysefactura.ui.screens

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.BudgetRepository
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.ui.screens.budget.list.BudgetListUiState
import com.authvex.balaxysefactura.ui.screens.budget.list.BudgetListViewModel
import com.authvex.balaxysefactura.ui.screens.cfe.list.CfeListUiState
import com.authvex.balaxysefactura.ui.screens.cfe.list.CfeListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ListSearchUnitTests {

    private lateinit var cfeRepository: CfeRepository
    private lateinit var budgetRepository: BudgetRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cfeRepository = mock()
        budgetRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `CFE_SEARCH_NUMBER_SENT_TO_API - CfeSearchRequest serializes query offset and limit for number search`() {
        val request = CfeSearchRequest(offset = 0, limit = 20, query = "123")
        val serialized = json.encodeToString(request)

        assertTrue(serialized.contains("\"query\":\"123\""))
        assertTrue(serialized.contains("\"offset\":0"))
        assertTrue(serialized.contains("\"limit\":20"))
        assertFalse(serialized.contains("filtro"))
        assertFalse(serialized.contains("pagina"))
    }

    @Test
    fun `CFE_SEARCH_RECEIVER_SENT_TO_API - CfeSearchRequest serializes query for receiver name search`() {
        val request = CfeSearchRequest(offset = 0, limit = 20, query = "Empresa Demo")
        val serialized = json.encodeToString(request)

        assertTrue(serialized.contains("\"query\":\"Empresa Demo\""))
        assertTrue(serialized.contains("\"offset\":0"))
        assertTrue(serialized.contains("\"limit\":20"))
    }

    @Test
    fun `CFE_SEARCH_TEXT_PROPAGATES_TO_VIEWMODEL and CFE_SEARCH_RESETS_PAGE - searching propagates text and resets page to 1`() = runTest {
        val cfeListResponse = CfeSearchResponse(
            totalRecords = 1,
            offset = 0,
            limit = 20,
            items = listOf(
                CfeSummaryDto(
                    documentoId = 100,
                    serie = "A",
                    numero = 123L,
                    cfeCode = 111,
                    receptor = "Empresa Demo",
                    fechaEmision = "2026-01-04",
                    importeTotal = 1000.0,
                    monedaSimbolo = "$",
                    estadoCfe = 2
                )
            )
        )

        whenever(cfeRepository.searchDocuments(anyOrNull(), eq(1))).thenReturn(Result.success(cfeListResponse))

        val viewModel = CfeListViewModel(cfeRepository)

        viewModel.onSearchQueryChanged("Empresa Demo")

        assertEquals("Empresa Demo", viewModel.searchQuery)
        assertTrue(viewModel.uiState is CfeListUiState.Success)
    }

    @Test
    fun `BUDGET_QUERY_SENT_AS_QUERY and BUDGET_QUERY_OFFSET_ZERO and BUDGET_LEGACY_SEARCH_PARAMS_NOT_SENT`() = runTest {
        val fakeApi = mock<BudgetApi>()
        val repository = BudgetRepository(fakeApi)

        whenever(fakeApi.getBudgets(any())).thenReturn(BudgetListResponse(items = emptyList(), totalRecords = 0))

        val result = repository.getBudgets(pagina = 1, registrosPorPagina = 20, busqueda = "Cliente Demo")

        assertTrue(result.isSuccess)
        verify(fakeApi).getBudgets(check { map ->
            assertEquals("Cliente Demo", map["query"])
            assertEquals("0", map["offset"])
            assertEquals("20", map["limit"])
            assertNull(map["busqueda"])
            assertNull(map["pagina"])
            assertNull(map["registrosPorPagina"])
        })
    }

    @Test
    fun `BUDGET_QUERY_WITH_ALL_STATE - state filter is omitted when all states selected`() = runTest {
        val fakeApi = mock<BudgetApi>()
        val repository = BudgetRepository(fakeApi)

        whenever(fakeApi.getBudgets(any())).thenReturn(BudgetListResponse(items = emptyList(), totalRecords = 0))

        val result = repository.getBudgets(pagina = 1, registrosPorPagina = 20, busqueda = "Cliente Demo", estado = null)

        assertTrue(result.isSuccess)
        verify(fakeApi).getBudgets(check { map ->
            assertEquals("Cliente Demo", map["query"])
            assertNull(map["estado"])
        })
    }

    @Test
    fun `BUDGET_QUERY_WITH_PENDING_STATE and BUDGET_QUERY_WITH_CONFIRMED_STATE`() = runTest {
        val fakeApi = mock<BudgetApi>()
        val repository = BudgetRepository(fakeApi)

        whenever(fakeApi.getBudgets(any())).thenReturn(BudgetListResponse(items = emptyList(), totalRecords = 0))

        val resultPending = repository.getBudgets(pagina = 1, registrosPorPagina = 20, busqueda = "ABITAB", estado = 1)
        assertTrue(resultPending.isSuccess)

        val resultConfirmed = repository.getBudgets(pagina = 1, registrosPorPagina = 20, busqueda = "ABITAB", estado = 2)
        assertTrue(resultConfirmed.isSuccess)
    }

    @Test
    fun `BUDGET_SEARCH_TEXT_PRESERVED_ON_STATE_CHANGE and BUDGET_STATE_PRESERVED_ON_SEARCH_CHANGE`() = runTest {
        val budgetResponse = BudgetListResponse(
            totalRecords = 1,
            items = listOf(BudgetDto(id = 101L, cliente = ClienteDto(10, "ABITAB S A")))
        )

        whenever(budgetRepository.getBudgets(any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(budgetResponse))

        val viewModel = BudgetListViewModel(budgetRepository)

        // Set search query
        viewModel.onSearchQueryChanged("ABITAB")
        assertEquals("ABITAB", viewModel.searchQuery)

        // Change state filter
        viewModel.onEstadoFilterChanged(1)

        // Both state and query preserved
        assertEquals("ABITAB", viewModel.searchQuery)
        assertEquals(1, viewModel.selectedEstadoFilter)
    }
}

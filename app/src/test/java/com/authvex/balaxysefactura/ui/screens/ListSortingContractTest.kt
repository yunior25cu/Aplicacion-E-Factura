package com.authvex.balaxysefactura.ui.screens

import com.authvex.balaxysefactura.core.network.CfeSearchRequest
import com.authvex.balaxysefactura.core.network.CfeSearchResponse
import com.authvex.balaxysefactura.core.network.CfeSummaryDto
import com.authvex.balaxysefactura.core.network.BudgetDto
import com.authvex.balaxysefactura.core.network.BudgetListResponse
import com.authvex.balaxysefactura.core.repository.BudgetRepository
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.ui.screens.budget.list.BudgetListViewModel
import com.authvex.balaxysefactura.ui.screens.cfe.list.CfeListViewModel
import com.authvex.balaxysefactura.ui.screens.cfe.list.NumberSortDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ListSortingContractTest {

    private lateinit var cfeRepository: CfeRepository
    private lateinit var budgetRepository: BudgetRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private val prodJson = Json { ignoreUnknownKeys = true }

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
    fun `CFE_SORT_FIELDS_SURVIVE_ENCODE_DEFAULTS_FALSE - CfeSearchRequest includes ordering and sortDirection even with encodeDefaults false`() {
        val descRequest = CfeSearchRequest(offset = 0, limit = 20, query = null, ordering = "Numero", sortDirection = "desc")
        val descSerialized = prodJson.encodeToString(descRequest)
        val descObject = prodJson.parseToJsonElement(descSerialized).jsonObject

        assertTrue(descObject.containsKey("ordering"))
        assertTrue(descObject.containsKey("sortDirection"))
        assertEquals("Numero", descObject["ordering"]?.jsonPrimitive?.content)
        assertEquals("desc", descObject["sortDirection"]?.jsonPrimitive?.content)

        val ascRequest = CfeSearchRequest(offset = 0, limit = 20, query = null, ordering = "Numero", sortDirection = "asc")
        val ascSerialized = prodJson.encodeToString(ascRequest)
        val ascObject = prodJson.parseToJsonElement(ascSerialized).jsonObject

        assertTrue(ascObject.containsKey("ordering"))
        assertTrue(ascObject.containsKey("sortDirection"))
        assertEquals("Numero", ascObject["ordering"]?.jsonPrimitive?.content)
        assertEquals("asc", ascObject["sortDirection"]?.jsonPrimitive?.content)
    }

    @Test
    fun `CFE_DEFAULT_SORT_IS_NUMERO_DESC and CFE_SORT_SERVER_SIDE`() = runTest {
        val cfeItem = CfeSummaryDto(1, "A", 22020L, 111, "A", "2026-01-04", 100.0, "$", 2)
        whenever(cfeRepository.searchDocuments(anyOrNull(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(CfeSearchResponse(items = listOf(cfeItem), totalRecords = 1, offset = 0, limit = 20)))

        val viewModel = CfeListViewModel(cfeRepository)

        assertEquals(NumberSortDirection.DESC, viewModel.numberSortDirection)
        verify(cfeRepository).searchDocuments(query = null, page = 1, ordering = "Numero", sortDirection = "desc")
    }

    @Test
    fun `CFE_ASC_SORT_REQUEST and CFE_SORT_CHANGE_RESETS_PAGINATION`() = runTest {
        val cfeItem1 = CfeSummaryDto(1, "A", 22020L, 111, "A", "2026-01-04", 100.0, "$", 2)
        val cfeItem2 = CfeSummaryDto(2, "A", 22001L, 111, "A", "2026-01-04", 100.0, "$", 2)

        whenever(cfeRepository.searchDocuments(anyOrNull(), any(), eq("Numero"), eq("desc")))
            .thenReturn(Result.success(CfeSearchResponse(items = listOf(cfeItem1), totalRecords = 2, offset = 0, limit = 20)))
        whenever(cfeRepository.searchDocuments(anyOrNull(), any(), eq("Numero"), eq("asc")))
            .thenReturn(Result.success(CfeSearchResponse(items = listOf(cfeItem2), totalRecords = 2, offset = 0, limit = 20)))

        val viewModel = CfeListViewModel(cfeRepository)

        // Toggle to ASC
        viewModel.toggleNumberSort()

        assertEquals(NumberSortDirection.ASC, viewModel.numberSortDirection)
        verify(cfeRepository).searchDocuments(query = null, page = 1, ordering = "Numero", sortDirection = "asc")
    }

    @Test
    fun `BUDGET_DEFAULT_SORT_IS_DOCUMENTO_NUMERO_DESC and BUDGET_SORT_SERVER_SIDE`() = runTest {
        val budget = BudgetDto(id = 100L, numero = 3)
        whenever(budgetRepository.getBudgets(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(BudgetListResponse(items = listOf(budget), totalRecords = 1)))

        val viewModel = BudgetListViewModel(budgetRepository)

        assertEquals(NumberSortDirection.DESC, viewModel.numberSortDirection)
        verify(budgetRepository).getBudgets(
            pagina = 1,
            registrosPorPagina = 20,
            busqueda = null,
            estado = null,
            idCliente = null,
            ordering = "Documento.Numero",
            sortDirection = "desc"
        )
    }

    @Test
    fun `BUDGET_ASC_SORT_REQUEST and BUDGET_STATUS_FILTER_PRESERVES_SORT`() = runTest {
        val budget1 = BudgetDto(id = 100L, numero = 3)
        val budget2 = BudgetDto(id = 101L, numero = 1)

        whenever(budgetRepository.getBudgets(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), eq("Documento.Numero"), eq("desc")))
            .thenReturn(Result.success(BudgetListResponse(items = listOf(budget1), totalRecords = 2)))
        whenever(budgetRepository.getBudgets(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), eq("Documento.Numero"), eq("asc")))
            .thenReturn(Result.success(BudgetListResponse(items = listOf(budget2), totalRecords = 2)))

        val viewModel = BudgetListViewModel(budgetRepository)

        // Toggle to ASC
        viewModel.toggleNumberSort()

        assertEquals(NumberSortDirection.ASC, viewModel.numberSortDirection)
        verify(budgetRepository).getBudgets(
            pagina = 1,
            registrosPorPagina = 20,
            busqueda = null,
            estado = null,
            idCliente = null,
            ordering = "Documento.Numero",
            sortDirection = "asc"
        )

        // Filter by estado = 1 (Sin Confirmar) -> preserves ASC sorting!
        viewModel.onEstadoFilterChanged(1)

        verify(budgetRepository).getBudgets(
            pagina = 1,
            registrosPorPagina = 20,
            busqueda = null,
            estado = 1,
            idCliente = null,
            ordering = "Documento.Numero",
            sortDirection = "asc"
        )
    }
}

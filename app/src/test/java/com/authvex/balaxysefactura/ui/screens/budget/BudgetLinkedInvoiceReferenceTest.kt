package com.authvex.balaxysefactura.ui.screens.budget

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.BudgetRepository
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.ui.screens.budget.detail.BudgetDetailUiState
import com.authvex.balaxysefactura.ui.screens.budget.detail.BudgetDetailViewModel
import com.authvex.balaxysefactura.ui.screens.budget.list.BudgetCfeReferenceState
import com.authvex.balaxysefactura.ui.screens.budget.list.BudgetListViewModel
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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetLinkedInvoiceReferenceTest {

    private lateinit var budgetRepository: BudgetRepository
    private lateinit var cfeRepository: CfeRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        budgetRepository = mock()
        cfeRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `BUDGET_DETAIL_LINKED_INVOICE_SHOWS_CFE_SERIE_NUMERO and DOES_NOT_USE_INTERNAL_FOLIO`() = runTest {
        val budget = BudgetDto(
            id = 100L,
            factura = BudgetFacturaDto(id = 500L, folio = "FA-131/01/2026")
        )

        val cfeDetail = CfeDetailDto(
            documentoId = 500,
            serie = "A",
            numero = 125L,
            cfeCode = 111,
            estadoCfe = 2,
            estadoReceptor = 1,
            receptor = "ABITAB S A",
            fechaEmision = "2026-01-04",
            fechaConfirmacion = "2026-01-04",
            fechaEnvioUtc = null,
            fechaAceptadoUtc = null,
            importeTotal = 1000.0,
            iva = 180.0,
            monedaCodigo = "UYU",
            monedaSimbolo = "$",
            ultimoError = null
        )

        whenever(budgetRepository.getBudgetById(100L)).thenReturn(Result.success(budget))
        whenever(cfeRepository.getDocumentDetail(500)).thenReturn(Result.success(cfeDetail))

        val viewModel = BudgetDetailViewModel(budgetRepository, cfeRepository, 100L)

        assertTrue(viewModel.uiState is BudgetDetailUiState.Success)
        assertEquals("CFE: A-125", viewModel.cfeReferenceLabel)
        assertFalse(viewModel.cfeReferenceLabel!!.contains("FA-131/01/2026"))
    }

    @Test
    fun `BUDGET_LIST_CFE_REFERENCE_RECOMPOSES_AFTER_ASYNC_LOAD`() = runTest {
        val budget = BudgetDto(
            id = 100L,
            factura = BudgetFacturaDto(id = 500L, folio = "PF-2/01/2026")
        )

        val cfeDetail = CfeDetailDto(
            documentoId = 500,
            serie = "A",
            numero = 22020L,
            cfeCode = 111,
            estadoCfe = 2,
            estadoReceptor = 1,
            receptor = "ABITAB S A",
            fechaEmision = "2026-01-04",
            fechaConfirmacion = "2026-01-04",
            fechaEnvioUtc = null,
            fechaAceptadoUtc = null,
            importeTotal = 1000.0,
            iva = 180.0,
            monedaCodigo = "UYU",
            monedaSimbolo = "$",
            ultimoError = null
        )

        whenever(budgetRepository.getBudgets(any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(BudgetListResponse(items = listOf(budget), totalRecords = 1)))
        whenever(cfeRepository.getDocumentDetail(500)).thenReturn(Result.success(cfeDetail))

        val viewModel = BudgetListViewModel(budgetRepository, cfeRepository)

        val state = viewModel.cfeReferences[500L]
        assertTrue(state is BudgetCfeReferenceState.Resolved)
        assertEquals("Factura CFE A-22020", state!!.getDisplayLabel())
        assertFalse(state.getDisplayLabel().contains("PF-2/01/2026"))
    }

    @Test
    fun `BUDGET_LIST_LOADING_IS_NOT_RENDERED_AS_PENDING`() {
        val loadingState = BudgetCfeReferenceState.Loading
        assertEquals("Factura vinculada", loadingState.getDisplayLabel())
        assertNotEquals("Factura CFE pendiente", loadingState.getDisplayLabel())
    }

    @Test
    fun `BUDGET_LIST_FAILED_LOOKUP_IS_NOT_MASKED_AS_LOADING`() {
        val failedState = BudgetCfeReferenceState.Failed
        assertEquals("CFE no disponible", failedState.getDisplayLabel())
        assertNotEquals("Factura vinculada", failedState.getDisplayLabel())
    }

    @Test
    fun `BUDGET_LIST_PENDING_ONLY_AFTER_SUCCESS_WITHOUT_FISCAL_NUMBER`() = runTest {
        val budget = BudgetDto(
            id = 100L,
            factura = BudgetFacturaDto(id = 500L, folio = "FA-131/01/2026")
        )

        val cfeDetailPending = CfeDetailDto(
            documentoId = 500,
            serie = null,
            numero = null,
            cfeCode = 111,
            estadoCfe = 0,
            estadoReceptor = null,
            receptor = "ABITAB S A",
            fechaEmision = null,
            fechaConfirmacion = null,
            fechaEnvioUtc = null,
            fechaAceptadoUtc = null,
            importeTotal = 1000.0,
            iva = 180.0,
            monedaCodigo = "UYU",
            monedaSimbolo = "$",
            ultimoError = null
        )

        whenever(budgetRepository.getBudgets(any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(BudgetListResponse(items = listOf(budget), totalRecords = 1)))
        whenever(cfeRepository.getDocumentDetail(500)).thenReturn(Result.success(cfeDetailPending))

        val viewModel = BudgetListViewModel(budgetRepository, cfeRepository)

        val state = viewModel.cfeReferences[500L]
        assertTrue(state is BudgetCfeReferenceState.Pending)
        assertEquals("Factura CFE pendiente", state!!.getDisplayLabel())
    }

    @Test
    fun `BUDGET_LIST_CFE_LOOKUP_USES_FACTURA_ID_NOT_BUDGET_ID`() = runTest {
        val budget = BudgetDto(
            id = 2L,
            factura = BudgetFacturaDto(id = 1234L, folio = "FA-131/01/2026")
        )

        val cfeDetail = CfeDetailDto(
            documentoId = 1234,
            serie = "A",
            numero = 22020L,
            cfeCode = 111,
            estadoCfe = 2,
            estadoReceptor = 1,
            receptor = "A",
            fechaEmision = null,
            fechaConfirmacion = null,
            fechaEnvioUtc = null,
            fechaAceptadoUtc = null,
            importeTotal = 100.0,
            iva = 0.0,
            monedaCodigo = "UYU",
            monedaSimbolo = "$",
            ultimoError = null
        )

        whenever(budgetRepository.getBudgets(any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(BudgetListResponse(items = listOf(budget), totalRecords = 1)))
        whenever(cfeRepository.getDocumentDetail(1234)).thenReturn(Result.success(cfeDetail))

        val viewModel = BudgetListViewModel(budgetRepository, cfeRepository)

        // Verifies lookup was called with factura.id = 1234 and NOT budget.id = 2
        verify(cfeRepository).getDocumentDetail(1234)
        assertEquals("Factura CFE A-22020", viewModel.cfeReferences[1234L]?.getDisplayLabel())
    }

    @Test
    fun `BUDGET_LIST_REFRESH_RETRIES_PENDING_REFERENCE`() = runTest {
        val budget = BudgetDto(
            id = 100L,
            factura = BudgetFacturaDto(id = 500L, folio = "FA-131/01/2026")
        )

        val cfeDetailPending = CfeDetailDto(documentoId = 500, serie = null, numero = null, cfeCode = 111, estadoCfe = 0, estadoReceptor = null, receptor = "A", fechaEmision = null, fechaConfirmacion = null, fechaEnvioUtc = null, fechaAceptadoUtc = null, importeTotal = 100.0, iva = 0.0, monedaCodigo = "UYU", monedaSimbolo = "$", ultimoError = null)
        val cfeDetailResolved = CfeDetailDto(documentoId = 500, serie = "A", numero = 22020L, cfeCode = 111, estadoCfe = 2, estadoReceptor = 1, receptor = "A", fechaEmision = "2026-01-04", fechaConfirmacion = "2026-01-04", fechaEnvioUtc = null, fechaAceptadoUtc = null, importeTotal = 100.0, iva = 0.0, monedaCodigo = "UYU", monedaSimbolo = "$", ultimoError = null)

        whenever(budgetRepository.getBudgets(any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(BudgetListResponse(items = listOf(budget), totalRecords = 1)))
        whenever(cfeRepository.getDocumentDetail(500))
            .thenReturn(Result.success(cfeDetailPending))
            .thenReturn(Result.success(cfeDetailResolved))

        val viewModel = BudgetListViewModel(budgetRepository, cfeRepository)

        assertEquals("Factura CFE pendiente", viewModel.cfeReferences[500L]?.getDisplayLabel())

        // Refresh list -> invalidates pending and retries
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val refreshedState = viewModel.cfeReferences[500L]
        assertTrue(refreshedState is BudgetCfeReferenceState.Resolved)
        assertEquals("Factura CFE A-22020", refreshedState!!.getDisplayLabel())
    }

    @Test
    fun `BUDGET_CFE_REFERENCE_IS_CACHED`() = runTest {
        val budget1 = BudgetDto(id = 100L, factura = BudgetFacturaDto(id = 500L, folio = "FA-131/01/2026"))
        val budget2 = BudgetDto(id = 101L, factura = BudgetFacturaDto(id = 500L, folio = "FA-131/01/2026"))

        val cfeDetail = CfeDetailDto(
            documentoId = 500,
            serie = "A",
            numero = 125L,
            cfeCode = 111,
            estadoCfe = 2,
            estadoReceptor = 1,
            receptor = "ABITAB S A",
            fechaEmision = "2026-01-04",
            fechaConfirmacion = "2026-01-04",
            fechaEnvioUtc = null,
            fechaAceptadoUtc = null,
            importeTotal = 1000.0,
            iva = 180.0,
            monedaCodigo = "UYU",
            monedaSimbolo = "$",
            ultimoError = null
        )

        whenever(budgetRepository.getBudgets(any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(BudgetListResponse(items = listOf(budget1, budget2), totalRecords = 2)))
        whenever(cfeRepository.getDocumentDetail(500)).thenReturn(Result.success(cfeDetail))

        val viewModel = BudgetListViewModel(budgetRepository, cfeRepository)

        verify(cfeRepository, times(1)).getDocumentDetail(500)
        assertEquals("Factura CFE A-125", viewModel.cfeReferences[500L]?.getDisplayLabel())
    }
}

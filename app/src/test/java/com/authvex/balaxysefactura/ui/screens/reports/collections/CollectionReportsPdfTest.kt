package com.authvex.balaxysefactura.ui.screens.reports.collections

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.core.repository.CollectionReportRepository
import com.authvex.balaxysefactura.ui.screens.reports.collections.aging.AgingReportUiState
import com.authvex.balaxysefactura.ui.screens.reports.collections.aging.AgingReportViewModel
import com.authvex.balaxysefactura.ui.screens.reports.collections.collected.CollectedReportUiState
import com.authvex.balaxysefactura.ui.screens.reports.collections.collected.CollectedReportViewModel
import com.authvex.balaxysefactura.ui.screens.reports.collections.receivables.ReceivablesReportUiState
import com.authvex.balaxysefactura.ui.screens.reports.collections.receivables.ReceivablesReportViewModel
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionReportsPdfTest {

    private lateinit var reportRepository: CollectionReportRepository
    private lateinit var cfeRepository: CfeRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        reportRepository = mock()
        cfeRepository = mock()

        runTest {
            val empresa = EmpresaDto(id = 10, nombre = "Empresa", moneda = CatalogoItemDto(50, "Pesos", "UYU"))
            whenever(cfeRepository.getEmpresa()).thenReturn(Result.success(empresa))
            whenever(cfeRepository.getClientes(anyOrNull())).thenReturn(Result.success(listOf(ClienteDto(10, "ABITAB S A"))))
            whenever(cfeRepository.getTasaCambios(any())).thenReturn(Result.success(listOf(
                TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0),
                TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.0)
            )))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `RECEIVABLES_REPORT_PDF_USES_ACCOUNTS_RECEIVABLE_ENDPOINT_AND_MULTICURRENCY_RULE`() = runTest {
        val dto = CuentasPorCobrarDto(
            idCliente = 10,
            denominacionCliente = "ABITAB S A",
            porCobrar = 1000.0,
            porCobrarOriginal = 25.0,
            importeCobrado = 500.0,
            importeCobradoOriginal = 12.5
        )

        whenever(reportRepository.getAccountsReceivable(any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(listOf(dto)))

        val viewModel = ReceivablesReportViewModel(reportRepository, cfeRepository)
        viewModel.loadReport()

        assertTrue(viewModel.uiState is ReceivablesReportUiState.Success)
        val successState = viewModel.uiState as ReceivablesReportUiState.Success

        // Base currency (UYU) uses base fields
        assertEquals(1000.0, successState.totalPorCobrar, 0.001)

        // Switch to USD
        viewModel.onMonedaSelected(TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.0))

        val usdSuccessState = viewModel.uiState as ReceivablesReportUiState.Success
        // Foreign currency (USD) uses original fields
        assertEquals(25.0, usdSuccessState.totalPorCobrar, 0.001)

        verify(reportRepository, org.mockito.kotlin.atLeastOnce()).getAccountsReceivable(any(), any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `AGING_REPORT_PDF_USES_AGING_ENDPOINT_AND_ALWAYS_BASE_CURRENCY`() = runTest {
        val agingDto = CuentasPorCobrarAgingDto(
            idCliente = 10,
            denominacionCliente = "ABITAB S A",
            totalNoVencidas = 500.0,
            totalVencidas1a30 = 200.0,
            totalVencidas31a60 = 0.0,
            totalVencidas61a90 = 0.0,
            totalVencidas91Mas = 0.0,
            total = 700.0,
            tieneFallbackVencimiento = true
        )

        whenever(reportRepository.getAccountsReceivableAging(any(), any(), anyOrNull(), eq(null), eq(1)))
            .thenReturn(Result.success(listOf(agingDto)))

        val viewModel = AgingReportViewModel(reportRepository, cfeRepository)
        viewModel.loadReport()

        assertTrue(viewModel.uiState is AgingReportUiState.Success)
        val successState = viewModel.uiState as AgingReportUiState.Success

        assertEquals(200.0, successState.totalVencido, 0.001)
        assertEquals(700.0, successState.grandTotal, 0.001)
        assertTrue(successState.hasFallback)

        verify(reportRepository, org.mockito.kotlin.atLeastOnce()).getAccountsReceivableAging(any(), any(), anyOrNull(), eq(null), eq(1))
    }

    @Test
    fun `COLLECTED_REPORT_PDF_USES_EXHAUSTIVE_PAGINATION`() = runTest {
        val cobro1 = CobroSummaryDto(id = 1L, folio = "CO-1", fechaEmision = "2026-10-10", estado = 2, total = 1000.0)
        val cobro2 = CobroSummaryDto(id = 2L, folio = "CO-2", fechaEmision = "2026-10-10", estado = 2, total = 500.0)

        whenever(reportRepository.getCollectedSummaryExhaustive(any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(listOf(cobro1, cobro2)))

        val viewModel = CollectedReportViewModel(reportRepository, cfeRepository)
        viewModel.loadReport()

        assertTrue(viewModel.uiState is CollectedReportUiState.Success)
        val successState = viewModel.uiState as CollectedReportUiState.Success

        // Contains ALL items in period (2 items, sum 1500, avg 750)
        assertEquals(2, successState.items.size)
        assertEquals(1500.0, successState.totalCobrado, 0.001)
        assertEquals(750.0, successState.ticketPromedio, 0.001)

        verify(reportRepository, org.mockito.kotlin.atLeastOnce()).getCollectedSummaryExhaustive(any(), any(), anyOrNull(), anyOrNull())
    }
}

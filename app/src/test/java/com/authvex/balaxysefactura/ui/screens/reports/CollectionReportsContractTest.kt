package com.authvex.balaxysefactura.ui.screens.reports

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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionReportsContractTest {

    private lateinit var reportApi: CollectionReportApi
    private lateinit var collectionApi: CollectionApi
    private lateinit var cfeRepository: CfeRepository
    private lateinit var reportRepository: CollectionReportRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        reportApi = mock()
        collectionApi = mock()
        cfeRepository = mock()
        reportRepository = CollectionReportRepository(reportApi, collectionApi)

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
    fun `RECEIVABLES_REPORT_USES_BACKEND_REPORT_ENDPOINT and SENDS_GROUPED_TYPE_2_AND_DEFAULT_STATE_1`() = runTest {
        whenever(reportApi.getAccountsReceivable(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), eq(50L), eq(1), eq(0), eq(2)))
            .thenReturn(listOf(
                CuentasPorCobrarDto(idCliente = 10L, denominacionCliente = "ABITAB S A", porCobrar = 1000.0, importeCobrado = 400.0),
                CuentasPorCobrarDto(idCliente = 20L, denominacionCliente = "CLIENTE DEMO", porCobrar = 500.0, importeCobrado = 100.0)
            ))

        val viewModel = ReceivablesReportViewModel(reportRepository, cfeRepository)

        assertTrue(viewModel.uiState is ReceivablesReportUiState.Success)
        val state = viewModel.uiState as ReceivablesReportUiState.Success
        assertEquals(1500.0, state.totalPorCobrar, 0.001)
        assertEquals(500.0, state.totalCobrado, 0.001)

        verify(reportApi).getAccountsReceivable(
            fechaDesde = any(),
            fechaHasta = any(),
            idAlmacen = anyOrNull(),
            idCliente = anyOrNull(),
            idFormaPago = anyOrNull(),
            idMoneda = eq(50L),
            estado = eq(1),
            estadoVencimiento = eq(0),
            tipoReporte = eq(2)
        )
    }

    @Test
    fun `AGING_REPORT_USES_BACKEND_AGING_ENDPOINT and TOTAL_OVERDUE_EXCLUDES_NOT_DUE`() = runTest {
        val agingDto = CuentasPorCobrarAgingDto(
            idCliente = 10L,
            denominacionCliente = "ABITAB S A",
            totalNoVencidas = 500.0,
            totalVencidas1a30 = 200.0,
            totalVencidas31a60 = 100.0,
            totalVencidas61a90 = 50.0,
            totalVencidas91Mas = 25.0,
            total = 875.0,
            tieneFallbackVencimiento = true
        )

        whenever(reportApi.getAccountsReceivableAging(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), eq(50L), eq(1), eq(0)))
            .thenReturn(listOf(agingDto))

        val viewModel = AgingReportViewModel(reportRepository, cfeRepository)

        assertTrue(viewModel.uiState is AgingReportUiState.Success)
        val state = viewModel.uiState as AgingReportUiState.Success

        assertEquals(500.0, state.sumNoVencido, 0.001)
        assertEquals(375.0, state.totalVencido, 0.001) // 200 + 100 + 50 + 25 = 375 (Excludes 500 NoVencido)
        assertEquals(875.0, state.grandTotal, 0.001)
        assertTrue(state.hasFallback)

        assertEquals(1, state.clientRanking.size)
        assertEquals("ABITAB S A", state.clientRanking.first().clientName)
        assertEquals(375.0, state.clientRanking.first().totalVencido, 0.001)
    }

    @Test
    fun `COLLECTED_REPORT_FETCHES_ALL_PAGES_BEFORE_SUMMARY and DOES_NOT_SUM_ONLY_FIRST_PAGE`() = runTest {
        val cobroPage1 = CobroSummaryDto(id = 1L, folio = "CO-1", fechaEmision = "2026-10-09", estado = 2, total = 1000.0, montoTotalBase = 1000.0)
        val cobroPage2 = CobroSummaryDto(id = 2L, folio = "CO-2", fechaEmision = "2026-10-09", estado = 2, total = 500.0, montoTotalBase = 500.0)

        whenever(collectionApi.getCobros(any()))
            .thenReturn(CobroListResponse(totalRecords = 2, items = listOf(cobroPage1)))
            .thenReturn(CobroListResponse(totalRecords = 2, items = listOf(cobroPage2)))

        val viewModel = CollectedReportViewModel(reportRepository, cfeRepository)

        assertTrue(viewModel.uiState is CollectedReportUiState.Success)
        val state = viewModel.uiState as CollectedReportUiState.Success

        assertEquals(2, state.cantidadCobros)
        assertEquals(1500.0, state.totalCobrado, 0.001) // 1000 + 500 = 1500 (Summed ALL pages!)
        assertEquals(750.0, state.ticketPromedio, 0.001)

        verify(collectionApi, times(2)).getCobros(any())
    }

    @Test
    fun `REPORTS_DEFAULT_TO_COMPANY_BASE_CURRENCY_ID`() = runTest {
        whenever(reportApi.getAccountsReceivable(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), eq(50L), anyOrNull(), any(), any()))
            .thenReturn(emptyList())

        val viewModel = ReceivablesReportViewModel(reportRepository, cfeRepository)

        assertEquals(50, viewModel.companyBaseCurrencyId)
        assertEquals(50, viewModel.selectedMoneda?.id)
    }
}

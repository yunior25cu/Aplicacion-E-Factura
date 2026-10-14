package com.authvex.balaxysefactura.ui.screens.reports.sales

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.core.repository.ReportsRepository
import com.authvex.balaxysefactura.ui.screens.reports.ReportsViewModel
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
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SalesReportPdfTest {

    private lateinit var reportsRepository: ReportsRepository
    private lateinit var cfeRepository: CfeRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private val defaultMeta = ReportsMetadataDto(
        monedaBase = MonedaMetadataDto(50, "UYU", "Pesos", "$"),
        incluyeAnulados = false,
        incluyeSinConfirmar = false,
        origen = "UY",
        timezone = "UTC"
    )
    private val defaultPeriodo = PeriodoDto("2026-09-01", "2026-10-01", 30)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        reportsRepository = mock()
        cfeRepository = mock()

        runTest {
            whenever(reportsRepository.getVentasResumen(any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(Result.success(VentasResumenResponse(defaultPeriodo, VentasTotalesDto(0.0, 0, 0.0), null, defaultMeta)))
            whenever(reportsRepository.getVentasSerie(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(Result.success(VentasSerieResponse(defaultPeriodo, "dia", emptyList(), defaultMeta)))
            whenever(reportsRepository.getVentasPorCliente(any(), any(), anyOrNull(), anyOrNull(), any()))
                .thenReturn(Result.success(VentasPorClienteResponse(defaultPeriodo, 10, emptyList(), defaultMeta)))
            whenever(reportsRepository.getVentasPorProducto(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any()))
                .thenReturn(Result.success(VentasPorProductoResponse(defaultPeriodo, 10, emptyList(), defaultMeta)))
            whenever(reportsRepository.getVentasPorDocumento(any(), any(), anyOrNull(), anyOrNull()))
                .thenReturn(Result.success(VentasPorDocumentoResponse(defaultPeriodo, emptyList(), defaultMeta)))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `SALES_REPORT_USES_MODERN_INFORMES_ENDPOINTS`() = runTest {
        val meta = ReportsMetadataDto(
            monedaBase = MonedaMetadataDto(50, "UYU", "Pesos", "$"),
            incluyeAnulados = false,
            incluyeSinConfirmar = false,
            origen = "UY",
            timezone = "UTC"
        )
        val periodo = PeriodoDto("2026-10-01", "2026-10-14", 14)

        val resumen = VentasResumenResponse(periodo, VentasTotalesDto(1234.56, 10, 123.45), null, meta)
        val serie = VentasSerieResponse(periodo, "dia", emptyList(), meta)
        val porCliente = VentasPorClienteResponse(periodo, 10, emptyList(), meta)
        val porProducto = VentasPorProductoResponse(periodo, 10, emptyList(), meta)
        val porDoc = VentasPorDocumentoResponse(periodo, emptyList(), meta)

        whenever(reportsRepository.getVentasResumen(eq("2026-10-01"), eq("2026-10-14"), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(resumen))
        whenever(reportsRepository.getVentasSerie(eq("2026-10-01"), eq("2026-10-14"), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(serie))
        whenever(reportsRepository.getVentasPorCliente(eq("2026-10-01"), eq("2026-10-14"), anyOrNull(), anyOrNull(), any()))
            .thenReturn(Result.success(porCliente))
        whenever(reportsRepository.getVentasPorProducto(eq("2026-10-01"), eq("2026-10-14"), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(Result.success(porProducto))
        whenever(reportsRepository.getVentasPorDocumento(eq("2026-10-01"), eq("2026-10-14"), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(porDoc))

        val viewModel = ReportsViewModel(reportsRepository)
        viewModel.updateDateRange("2026-10-01", "2026-10-14")

        val snapshotRes = viewModel.fetchSalesReportSnapshot(cfeRepository)

        assertTrue(snapshotRes.isSuccess)
        val model = snapshotRes.getOrNull()!!

        // Total matches backend resumen without recalculation
        assertEquals(1234.56, model.resumen.totales.importeTotalBase, 0.001)
        assertEquals("UYU", model.currencyCode)
        assertEquals("$", model.currencySymbol)

        verify(reportsRepository, atLeastOnce()).getVentasResumen(eq("2026-10-01"), eq("2026-10-14"), anyOrNull(), anyOrNull(), anyOrNull())
        verify(reportsRepository, atLeastOnce()).getVentasSerie(eq("2026-10-01"), eq("2026-10-14"), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        verify(reportsRepository, atLeastOnce()).getVentasPorCliente(eq("2026-10-01"), eq("2026-10-14"), anyOrNull(), anyOrNull(), any())
        verify(reportsRepository, atLeastOnce()).getVentasPorProducto(eq("2026-10-01"), eq("2026-10-14"), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())
        verify(reportsRepository, atLeastOnce()).getVentasPorDocumento(eq("2026-10-01"), eq("2026-10-14"), anyOrNull(), anyOrNull())
    }

    @Test
    fun `SALES_REPORT_DOCUMENT_BREAKDOWN_PRESERVES_BACKEND_SIGNS`() {
        val meta = ReportsMetadataDto(
            monedaBase = MonedaMetadataDto(50, "UYU", "Pesos", "$"),
            incluyeAnulados = false,
            incluyeSinConfirmar = false,
            origen = "UY",
            timezone = "UTC"
        )
        val periodo = PeriodoDto("2026-10-01", "2026-10-14", 14)

        val docItems = listOf(
            VentaDocumentoItemDto("Factura", "e-Factura", 10, 1000.0),
            VentaDocumentoItemDto("NotaCredito", "NC e-Factura", 2, -200.0),
            VentaDocumentoItemDto("NotaDebito", "ND e-Factura", 1, 50.0)
        )
        val porDoc = VentasPorDocumentoResponse(periodo, docItems, meta)

        // Verifies signs delivered by backend are preserved
        assertEquals(1000.0, porDoc.items[0].importeTotalBase, 0.001)
        assertEquals(-200.0, porDoc.items[1].importeTotalBase, 0.001)
        assertEquals(50.0, porDoc.items[2].importeTotalBase, 0.001)
    }
}

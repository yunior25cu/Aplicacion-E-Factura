package com.authvex.balaxysefactura.ui.screens.reports

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.ReportsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    class FakeReportsApi : ReportsApi {
        override suspend fun getVentasResumen(fechaDesde: String, fechaHasta: String, almacenId: Int?, clienteId: Int?, soloElectronicas: Boolean?, compararPeriodoAnterior: Boolean) = 
            VentasResumenResponse(PeriodoDto(fechaDesde, fechaHasta, 31), VentasTotalesDto(1000.0, 10, 100.0), null, mockMetadata())

        override suspend fun getVentasSerie(fechaDesde: String, fechaHasta: String, granularidad: String?, almacenId: Int?, clienteId: Int?, soloElectronicas: Boolean?) = 
            VentasSerieResponse(PeriodoDto(fechaDesde, fechaHasta, 31), "dia", emptyList(), mockMetadata())

        override suspend fun getVentasPorCliente(fechaDesde: String, fechaHasta: String, almacenId: Int?, soloElectronicas: Boolean?, top: Int) = 
            VentasPorClienteResponse(PeriodoDto(fechaDesde, fechaHasta, 31), top, emptyList(), mockMetadata())

        override suspend fun getVentasPorProducto(fechaDesde: String, fechaHasta: String, almacenId: Int?, categoriaId: Int?, subcategoriaId: Int?, soloElectronicas: Boolean?, top: Int) = 
            VentasPorProductoResponse(PeriodoDto(fechaDesde, fechaHasta, 31), top, emptyList(), mockMetadata())

        override suspend fun getVentasPorDocumento(fechaDesde: String, fechaHasta: String, almacenId: Int?, soloElectronicas: Boolean?) = 
            VentasPorDocumentoResponse(PeriodoDto(fechaDesde, fechaHasta, 31), emptyList(), mockMetadata())

        override suspend fun getCategorias(id: Int?): List<CategoriaDto> = emptyList()

        private fun mockMetadata() = ReportsMetadataDto(
            monedaBase = MonedaMetadataDto(1, "UYU", "Peso", "$"),
            incluyeAnulados = false,
            incluyeSinConfirmar = false,
            origen = "erp",
            timezone = "UTC"
        )
    }

    private val repository = ReportsRepository(FakeReportsApi())
    private lateinit var viewModel: ReportsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ReportsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads with last 30 days preset`() = runTest {
        val calendar = Calendar.getInstance()
        val expectedHasta = sdf.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -29)
        val expectedDesde = sdf.format(calendar.time)
        
        assertEquals(expectedDesde, viewModel.fechaDesde)
        assertEquals(expectedHasta, viewModel.fechaHasta)
        assertTrue(viewModel.resumenState is ReportsUiState.Success)
    }

    @Test
    fun `applyPreset TODAY updates dates correctly`() = runTest {
        val today = sdf.format(Date())
        viewModel.applyPreset(DatePreset.TODAY)
        assertEquals(today, viewModel.fechaDesde)
        assertEquals(today, viewModel.fechaHasta)
    }

    @Test
    fun `applyPreset THIS_MONTH updates dates correctly`() = runTest {
        val calendar = Calendar.getInstance()
        val expectedHasta = sdf.format(calendar.time)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val expectedDesde = sdf.format(calendar.time)
        
        viewModel.applyPreset(DatePreset.THIS_MONTH)
        assertEquals(expectedDesde, viewModel.fechaDesde)
        assertEquals(expectedHasta, viewModel.fechaHasta)
    }

    @Test
    fun `updateDateRange triggers reload`() = runTest {
        viewModel.updateDateRange("2024-01-01", "2024-01-31")
        assertEquals("2024-01-01", viewModel.fechaDesde)
        assertEquals("2024-01-31", viewModel.fechaHasta)
        assertTrue(viewModel.resumenState is ReportsUiState.Success)
    }
}

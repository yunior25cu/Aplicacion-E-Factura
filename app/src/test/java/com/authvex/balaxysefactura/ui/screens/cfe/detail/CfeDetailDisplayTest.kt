package com.authvex.balaxysefactura.ui.screens.cfe.detail

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
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
class CfeDetailDisplayTest {

    private lateinit var repository: CfeRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `CFE_DETAIL_BASE_CURRENCY_SHOWS_BASE_TOTAL`() = runTest {
        val detail = CfeDetailDto(
            documentoId = 100,
            serie = "A",
            numero = 123L,
            cfeCode = 111,
            estadoCfe = 2,
            estadoReceptor = 1,
            receptor = "ABITAB S A",
            fechaEmision = "2026-01-04",
            fechaConfirmacion = "2026-01-04",
            fechaEnvioUtc = null,
            fechaAceptadoUtc = null,
            importeTotal = 500.0,
            iva = 90.0,
            monedaCodigo = "UYU",
            monedaSimbolo = "$",
            ultimoError = null
        )

        val empresa = EmpresaDto(id = 10, nombre = "Empresa", moneda = CatalogoItemDto(50, "Pesos", "UYU"))
        val factura = BudgetDto(
            id = 100L,
            moneda = CatalogoItemDto(50, "Pesos", "UYU"),
            importeTotalBase = 500.0,
            importeTotalOriginal = 0.0,
            tasaCambio = 1.0
        )

        whenever(repository.getDocumentDetail(100)).thenReturn(Result.success(detail))
        whenever(repository.getEmpresa()).thenReturn(Result.success(empresa))
        whenever(repository.getFacturaById(100L)).thenReturn(Result.success(factura))

        val viewModel = CfeDetailViewModel(repository, 100L)

        assertTrue(viewModel.uiState is CfeDetailUiState.Success)
        assertEquals("$ 500.00", viewModel.formattedTotalText)
    }

    @Test
    fun `CFE_DETAIL_FOREIGN_CURRENCY_SHOWS_ORIGINAL_TOTAL and DOES_NOT_DIVIDE_BASE_BY_RATE`() = runTest {
        val detail = CfeDetailDto(
            documentoId = 200,
            serie = "A",
            numero = 456L,
            cfeCode = 111,
            estadoCfe = 2,
            estadoReceptor = 1,
            receptor = "ABITAB S A",
            fechaEmision = "2026-01-04",
            fechaConfirmacion = "2026-01-04",
            fechaEnvioUtc = null,
            fechaAceptadoUtc = null,
            importeTotal = 402.10, // CfeDetailDto stores base amount
            iva = 72.43,
            monedaCodigo = "USD",
            monedaSimbolo = "$",
            ultimoError = null
        )

        val empresa = EmpresaDto(id = 10, nombre = "Empresa", moneda = CatalogoItemDto(50, "Pesos", "UYU"))
        val factura = BudgetDto(
            id = 200L,
            moneda = CatalogoItemDto(51, "Dólar", "USD"),
            importeTotalBase = 402.10,
            importeTotalOriginal = 10.00,
            tasaCambio = 40.210
        )

        whenever(repository.getDocumentDetail(200)).thenReturn(Result.success(detail))
        whenever(repository.getEmpresa()).thenReturn(Result.success(empresa))
        whenever(repository.getFacturaById(200L)).thenReturn(Result.success(factura))

        val viewModel = CfeDetailViewModel(repository, 200L)

        assertTrue(viewModel.uiState is CfeDetailUiState.Success)
        assertEquals("USD 10.00", viewModel.formattedTotalText)
        assertNotEquals("USD 402.10", viewModel.formattedTotalText)
        assertNotEquals("$ 402.10", viewModel.formattedTotalText)
    }

    @Test
    fun `CFE_DETAIL_FOREIGN_NC_SHOWS_ORIGINAL_TOTAL`() = runTest {
        val detail = CfeDetailDto(
            documentoId = 300,
            serie = "A",
            numero = 789L,
            cfeCode = 112, // NC e-Factura
            estadoCfe = 2,
            estadoReceptor = 1,
            receptor = "ABITAB S A",
            fechaEmision = "2026-01-04",
            fechaConfirmacion = "2026-01-04",
            fechaEnvioUtc = null,
            fechaAceptadoUtc = null,
            importeTotal = 402.10,
            iva = 72.43,
            monedaCodigo = "USD",
            monedaSimbolo = "$",
            ultimoError = null
        )

        val empresa = EmpresaDto(id = 10, nombre = "Empresa", moneda = CatalogoItemDto(50, "Pesos", "UYU"))
        val devolucion = BudgetDto(
            id = 300L,
            moneda = CatalogoItemDto(51, "Dólar", "USD"),
            importeTotalBase = 402.10,
            importeTotalOriginal = 10.00,
            tasaCambio = 40.210
        )

        whenever(repository.getDocumentDetail(300)).thenReturn(Result.success(detail))
        whenever(repository.getEmpresa()).thenReturn(Result.success(empresa))
        whenever(repository.getDevolucionById(300L)).thenReturn(Result.success(devolucion))

        val viewModel = CfeDetailViewModel(repository, 300L)

        assertTrue(viewModel.uiState is CfeDetailUiState.Success)
        assertEquals("USD 10.00", viewModel.formattedTotalText)
    }

    @Test
    fun `CFE_DETAIL_FACTURA_FETCH_FAILURE_DOES_NOT_BREAK_DETAIL`() = runTest {
        val detail = CfeDetailDto(
            documentoId = 400,
            serie = "A",
            numero = 999L,
            cfeCode = 111,
            estadoCfe = 2,
            estadoReceptor = 1,
            receptor = "ABITAB S A",
            fechaEmision = "2026-01-04",
            fechaConfirmacion = "2026-01-04",
            fechaEnvioUtc = null,
            fechaAceptadoUtc = null,
            importeTotal = 500.0,
            iva = 90.0,
            monedaCodigo = "UYU",
            monedaSimbolo = "$",
            ultimoError = null
        )

        whenever(repository.getDocumentDetail(400)).thenReturn(Result.success(detail))
        whenever(repository.getEmpresa()).thenReturn(Result.failure(Exception("Network error")))
        whenever(repository.getFacturaById(400L)).thenReturn(Result.failure(Exception("Network error")))

        val viewModel = CfeDetailViewModel(repository, 400L)

        assertTrue(viewModel.uiState is CfeDetailUiState.Success)
        assertEquals("$ 500.00", viewModel.formattedTotalText)
    }
}

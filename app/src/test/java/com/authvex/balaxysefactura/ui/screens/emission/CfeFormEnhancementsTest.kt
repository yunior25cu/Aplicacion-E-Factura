package com.authvex.balaxysefactura.ui.screens.emission

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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CfeFormEnhancementsTest {

    private lateinit var repository: CfeRepository
    private lateinit var viewModel: EmissionViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()

        runTest {
            val pos = PuntoVentaDto(1, "Caja 1", 1, true, false)
            val type = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")
            whenever(repository.getPuntosVenta()).thenReturn(Result.success(listOf(pos)))
            whenever(repository.getDocumentosHabilitados(1)).thenReturn(Result.success(listOf(CfeFiscalDocumentAvailabilityGroupDto(1, listOf(type)))))
            whenever(repository.getClientes(anyOrNull())).thenReturn(Result.success(listOf(ClienteDto(10, "ABITAB S A", ruc = "211234560012"))))
            whenever(repository.getProductos(anyOrNull())).thenReturn(Result.success(listOf(ProductoDto(50, "Prod 1", precio = 100.0, tasaIva = 0.22))))
            whenever(repository.getTasaCambios(any())).thenReturn(Result.success(listOf(TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0))))
            whenever(repository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(2, "Almacén Central"))))
        }

        viewModel = EmissionViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `CFE_DEFAULT_EMISSION_DATE_IS_TODAY and CFE_DEFAULT_CONFIRMATION_DATE_IS_TODAY`() = runTest {
        assertNotNull(viewModel.fechaEmision)
        assertNotNull(viewModel.fechaConfirmacion)
        assertEquals(viewModel.fechaEmision, viewModel.fechaConfirmacion)
    }

    @Test
    fun `CFE_SELECTED_EMISSION_DATE_REACHES_REQUEST and CFE_SELECTED_CONFIRMATION_DATE_REACHES_REQUEST`() = runTest {
        val pos = PuntoVentaDto(1, "Caja 1", 1, true, false)
        val type = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")

        viewModel.selectedCliente = ClienteDto(10, "ABITAB S A", ruc = "211234560012")
        viewModel.selectedAlmacen = CatalogoItemDto(2, "Almacén Central")
        viewModel.selectedMoneda = TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0)
        viewModel.selectedFiscalType = type
        viewModel.selectedPOS = pos
        viewModel.lineas.add(LineaForm(ProductoDto(50, "Prod 1", precio = 100.0, tasaIva = 0.22), cantidad = 1.0, precioUnitario = 100.0))

        viewModel.fechaEmision = "2026-10-15"
        viewModel.fechaConfirmacion = "2026-10-20"
        viewModel.preciosIncluyenIva = false

        whenever(repository.caePrecheck(any(), any(), anyOrNull(), any())).thenReturn(Result.success(CaePrecheckResultDto(hasValidCae = true, message = "OK")))
        whenever(repository.validateCfe(any(), any(), any(), anyOrNull())).thenReturn(Result.success(CfeValidateResponseDto(isValid = true)))
        whenever(repository.emitCfe(any(), any())).thenReturn(Result.success(CfeEmitResponse("1", null)))

        whenever(repository.createFacturaElectronicDraft(check { request ->
            assertEquals("2026-10-15", request.fechaEmision)
            assertEquals("2026-10-20", request.fechaConfirmacion)
            assertEquals(false, request.preciosIncluyenIva)
        })).thenReturn(Result.success(999L))

        viewModel.proceedToEmission()
    }

    @Test
    fun `CFE_EMISSION_AFTER_CONFIRMATION_REJECTED - fails when emission date is after confirmation date`() = runTest {
        val pos = PuntoVentaDto(1, "Caja 1", 1, true, false)
        val type = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")

        viewModel.selectedCliente = ClienteDto(10, "ABITAB S A", ruc = "211234560012", tipoDocumentoIdentificacion = 2)
        viewModel.selectedAlmacen = CatalogoItemDto(2, "Almacén Central")
        viewModel.selectedMoneda = TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0)
        viewModel.selectedFiscalType = type
        viewModel.selectedPOS = pos
        viewModel.lineas.add(LineaForm(ProductoDto(50, "Prod 1", precio = 100.0, tasaIva = 0.22), cantidad = 1.0, precioUnitario = 100.0))

        viewModel.fechaEmision = "2026-10-25"
        viewModel.fechaConfirmacion = "2026-10-20" // Emission after confirmation!

        viewModel.proceedToEmission()

        assertTrue(viewModel.uiState is EmissionUiState.Error)
        val err = (viewModel.uiState as EmissionUiState.Error).error
        assertTrue(err.getDisplayMessage().contains("fecha de emisión no puede ser posterior"))
    }

    @Test
    fun `CFE_CONFIRMATION_DATE_CHANGE_RELOADS_RATE - changing confirmation date queries exchange rate for that date`() = runTest {
        whenever(repository.getTasaCambios("2026-12-01")).thenReturn(Result.success(listOf(
            TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0),
            TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 42.5)
        )))

        viewModel.onFechaConfirmacionChanged("2026-12-01")

        assertEquals("2026-12-01", viewModel.fechaConfirmacion)
        verify(repository).getTasaCambios("2026-12-01")
    }

    @Test
    fun `CFE_PRICE_MODE_DOES_NOT_CHANGE_TAX_RATE_OR_C4 - toggle does not alter tax rate or C4 metadata`() = runTest {
        val prod = ProductoDto(50, "Prod 1", precio = 100.0, tasaIva = 0.22)
        val line = LineaForm(prod, cantidad = 1.0, precioUnitario = 100.0, indicadorFacturacionC4 = 3)

        viewModel.preciosIncluyenIva = true

        assertEquals(0.22, line.producto.tasaIva!!, 0.001)
        assertEquals(3, line.indicadorFacturacionC4)
    }
}

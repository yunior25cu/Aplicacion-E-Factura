package com.authvex.balaxysefactura.ui.screens.emission

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class EmissionFiscalLogicTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: CfeRepository = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial fiscal intent can be selected`() = runTest {
        setupCatalogs()
        val viewModel = EmissionViewModel(repository)
        advanceUntilIdle()

        viewModel.selectedFiscalIntent = FiscalIntent.E_TICKET
        assertEquals(FiscalIntent.E_TICKET, viewModel.selectedFiscalIntent)
    }

    @Test
    fun `full emission flow resolves fiscal intent correctly`() = runTest {
        setupCatalogs()
        val viewModel = EmissionViewModel(repository)
        advanceUntilIdle()
        fillBasicForm(viewModel)
        viewModel.selectedFiscalIntent = FiscalIntent.E_FACTURA

        val docId = 123L
        val fiscalOption = CfeTipoPermitidoDto(
            code = 111,
            name = "e-Factura",
            suggested = true,
            implementedInUi = true,
            puntoVentaId = 3,
            serie = "A",
            proximoNumero = 100
        )

        whenever(repository.createFactura(any())).thenReturn(Result.success(docId))
        whenever(repository.getFactura(docId)).thenReturn(Result.success(FacturaResponse(docId)))
        whenever(repository.getTiposPermitidos()).thenReturn(Result.success(listOf(fiscalOption)))
        whenever(repository.emitCfe(eq(docId), any())).thenReturn(Result.success(CfeEmitResponse(true)))
        whenever(repository.getCfeStatus(docId)).thenReturn(Result.success(CfeStatusResponse(docId, 1)))

        viewModel.startEmissionFlow()
        advanceUntilIdle()

        // En la implementación de EmissionViewModel, si compatibleOptions.size == 1
        // isFiscalResolved no se setea a true en resolveFiscalIntent, sino que se ejecuta
        // executeFinalEmit directamente, y el UI state pasa a Success.
        assertTrue("Expected Success state but was ${viewModel.uiState}", viewModel.uiState is EmissionUiState.Success)
        assertEquals(3, viewModel.selectedResolution?.puntoVentaId)
        assertEquals(111, viewModel.selectedResolution?.cfeCode)
        assertEquals("A", viewModel.selectedResolution?.serie)
    }

    @Test
    fun `when fiscal intent match fails emission stops with error`() = runTest {
        setupCatalogs()
        val viewModel = EmissionViewModel(repository)
        advanceUntilIdle()
        fillBasicForm(viewModel)
        viewModel.selectedFiscalIntent = FiscalIntent.E_FACTURA

        val docId = 123L
        // Solo devuelve e-Ticket (101), pero la intención es e-Factura (111)
        val fiscalOption = CfeTipoPermitidoDto(101, "e-Ticket", true, true, 1, "A", 1)

        whenever(repository.createFactura(any())).thenReturn(Result.success(docId))
        whenever(repository.getFactura(docId)).thenReturn(Result.success(FacturaResponse(docId)))
        whenever(repository.getTiposPermitidos()).thenReturn(Result.success(listOf(fiscalOption)))

        viewModel.startEmissionFlow()
        advanceUntilIdle()

        assertFalse(viewModel.isFiscalResolved)
        assertNotNull(viewModel.resolutionError)
        assertEquals("No hay configuración fiscal válida para emitir este tipo de comprobante", viewModel.resolutionError)
    }

    private suspend fun setupCatalogs() {
        whenever(repository.getClientes()).thenReturn(Result.success(emptyList()))
        whenever(repository.getProductos()).thenReturn(Result.success(emptyList()))
        whenever(repository.getMonedas()).thenReturn(Result.success(emptyList()))
        whenever(repository.getAlmacenes()).thenReturn(Result.success(emptyList()))
        whenever(repository.getFormasPago()).thenReturn(Result.success(emptyList()))
        whenever(repository.getVencimientos()).thenReturn(Result.success(emptyList()))
        whenever(repository.getListasPrecio()).thenReturn(Result.success(emptyList()))
    }

    private fun fillBasicForm(vm: EmissionViewModel) {
        vm.selectedCliente = ClienteDto(1, "Test")
        vm.selectedMoneda = CatalogoItemDto(1, "Pesos", "UYU")
        vm.selectedAlmacen = CatalogoItemDto(1, "ALM")
        vm.selectedFormaPago = CatalogoItemDto(1, "CONT")
        vm.addLinea(ProductoDto(1, "Prod", precio = 10.0), 1.0)
    }
}

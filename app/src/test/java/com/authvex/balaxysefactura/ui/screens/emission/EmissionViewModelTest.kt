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
class EmissionViewModelTest {

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
    fun `initial load fetches catalogs and sets FormReady state`() = runTest {
        val clientes = listOf(ClienteDto(1, "Cliente 1"))
        val productos = listOf(ProductoDto(1, "Producto 1", precio = 100.0))
        val monedas = listOf(CatalogoItemDto(1, "Pesos", "UYU"))
        val almacenes = listOf(CatalogoItemDto(1, "Central"))
        val formasPago = listOf(CatalogoItemDto(1, "Contado"))

        whenever(repository.getClientes()).thenReturn(Result.success(clientes))
        whenever(repository.getProductos()).thenReturn(Result.success(productos))
        whenever(repository.getMonedas()).thenReturn(Result.success(monedas))
        whenever(repository.getAlmacenes()).thenReturn(Result.success(almacenes))
        whenever(repository.getFormasPago()).thenReturn(Result.success(formasPago))
        whenever(repository.getVencimientos()).thenReturn(Result.success(emptyList()))
        whenever(repository.getListasPrecio()).thenReturn(Result.success(emptyList()))

        val viewModel = EmissionViewModel(repository)
        advanceUntilIdle()
        
        assertTrue("State should be FormReady but was ${viewModel.uiState}", viewModel.uiState is EmissionUiState.FormReady)
        val state = viewModel.uiState as EmissionUiState.FormReady
        assertEquals(clientes, state.clientes)
        assertEquals("UYU", viewModel.selectedMoneda?.codigo)
    }

    @Test
    fun `emission flow resolves fiscal intent correctly`() = runTest {
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
        
        // Al ser opción única sugerida y validada, salta directo a Emit
        assertTrue("State should be Success but was ${viewModel.uiState}", viewModel.uiState is EmissionUiState.Success)
    }

    @Test
    fun `emission flow handles create failure`() = runTest {
        val cliente = ClienteDto(1, "Cliente 1")
        val product = ProductoDto(1, "P", precio = 10.0)
        
        whenever(repository.getClientes()).thenReturn(Result.success(listOf(cliente)))
        whenever(repository.getProductos()).thenReturn(Result.success(listOf(product)))
        whenever(repository.getMonedas()).thenReturn(Result.success(listOf(CatalogoItemDto(1, "Pesos", "UYU"))))
        whenever(repository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(1, "A"))))
        whenever(repository.getFormasPago()).thenReturn(Result.success(listOf(CatalogoItemDto(1, "F"))))
        whenever(repository.getVencimientos()).thenReturn(Result.success(emptyList()))
        whenever(repository.getListasPrecio()).thenReturn(Result.success(emptyList()))

        val viewModel = EmissionViewModel(repository)
        advanceUntilIdle()
        
        viewModel.selectedCliente = cliente
        viewModel.addLinea(product, 1.0)

        val error = AppError.ServerError(500)
        whenever(repository.createFactura(any())).thenReturn(Result.failure(error))

        viewModel.startEmissionFlow()
        advanceUntilIdle()

        assertTrue(viewModel.uiState is EmissionUiState.Error)
        assertEquals(error, (viewModel.uiState as EmissionUiState.Error).error)
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

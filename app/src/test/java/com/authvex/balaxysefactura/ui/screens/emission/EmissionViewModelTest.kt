package com.authvex.balaxysefactura.ui.screens.emission

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
        runBlocking {
            whenever(repository.getIndicadoresFacturacion(any(), any(), anyOrNull(), any())).thenReturn(Result.success(emptyList()))
            whenever(repository.getIndicadorSugerido(any(), any(), anyOrNull(), any(), anyOrNull(), any())).thenReturn(Result.success(CfeFiscalIndicadorSugeridoDto(null, null, false, "N/A")))
            whenever(repository.caePrecheck(any(), any(), anyOrNull(), any())).thenReturn(Result.success(CaePrecheckResultDto(true)))
            whenever(repository.validateCfe(any(), any(), any(), any())).thenReturn(Result.success(CfeValidateResponseDto(true)))
            whenever(repository.emitCfe(any(), any())).thenReturn(Result.success(CfeEmitResponse(true)))
            whenever(repository.getCfeStatus(any())).thenReturn(Result.success(CfeStatusResponse(123L, 1, "OK")))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load fetches points of sale`() = runTest {
        val pvs = listOf(PuntoVentaDto(1, "Main", 1, true, true))
        whenever(repository.getPuntosVenta()).thenReturn(Result.success(pvs))

        val viewModel = EmissionViewModel(repository)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState is EmissionUiState.SelectPOS)
        assertEquals(pvs, (viewModel.uiState as EmissionUiState.SelectPOS).puntosVenta)
    }

    @Test
    fun `proceedToEmission creates factura with correct payload and rule of 0 on base currency`() = runTest {
        val pv = PuntoVentaDto(1, "Main", 1, true, true)
        val item = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")
        val client = ClienteDto(932, "Test Client")
        val moneda = CatalogoItemDto(1, "UYU", "UYU")
        val almacen = CatalogoItemDto(10, "Deposito")
        val formapago = CatalogoItemDto(1, "Contado")
        val sugerencia = CfeFiscalIndicadorSugeridoDto(persistedValue = 16, suggestedValue = 16, isAutomatic = true, label = "IVA Minimo")
        
        whenever(repository.getPuntosVenta()).thenReturn(Result.success(listOf(pv)))
        whenever(repository.getDocumentosHabilitados(any())).thenReturn(Result.success(listOf(CfeFiscalDocumentAvailabilityGroupDto(1, listOf(item)))))
        whenever(repository.getMonedas()).thenReturn(Result.success(listOf(moneda)))
        whenever(repository.getAlmacenes()).thenReturn(Result.success(listOf(almacen)))
        whenever(repository.getFormasPago()).thenReturn(Result.success(listOf(formapago)))
        whenever(repository.getListasPrecio()).thenReturn(Result.success(emptyList()))
        whenever(repository.getVencimientos()).thenReturn(Result.success(emptyList()))
        whenever(repository.getIndicadoresFacturacion(any(), any(), anyOrNull(), any())).thenReturn(Result.success(emptyList()))
        whenever(repository.getIndicadorSugerido(any(), any(), anyOrNull(), any(), anyOrNull(), any())).thenReturn(Result.success(sugerencia))

        val viewModel = EmissionViewModel(repository)
        advanceUntilIdle()
        
        viewModel.selectPOS(pv)
        advanceUntilIdle()
        
        viewModel.selectFiscalType(item)
        advanceUntilIdle()
        
        viewModel.selectedCliente = client
        viewModel.selectedMoneda = moneda
        viewModel.selectedAlmacen = almacen
        viewModel.selectedFormaPago = formapago
        
        val product = ProductoDto(1001, "Product", "P001", 100.0, 0.22)
        viewModel.startLineConfiguration(product)
        advanceUntilIdle()
        
        // Ensure persistedValue from suggestion is used
        viewModel.confirmLineConfiguration(1.0, 100.0, viewModel.lineConfigurationSugerido?.persistedValue, viewModel.lineConfigurationSugerido?.suggestedValue, viewModel.lineConfigurationSugerido?.label)
        advanceUntilIdle()
        
        whenever(repository.createFactura(any())).thenReturn(Result.success(123L))

        viewModel.proceedToEmission()
        advanceUntilIdle()

        val captor = argumentCaptor<FacturaCreateDto>()
        verify(repository).createFactura(captor.capture())
        
        val payload = captor.firstValue
        
        // Header assertions
        assertEquals(122.0, payload.importeTotalBase, 0.0)
        
        // Lines assertions
        assertEquals(1, payload.documentoProductos.size)
        val line = payload.documentoProductos[0]
        assertEquals(16, line.indicadorFacturacionC4)
    }

    @Test
    fun `normalization rule - null 1 2 3 4 become null`() = runTest {
        val viewModel = setupViewModelForPayload()
        val product = ProductoDto(1001, "Product", "P001", 100.0, 0.22)
        
        // Add lines with different C4
        viewModel.startLineConfiguration(product)
        advanceUntilIdle()
        viewModel.confirmLineConfiguration(1.0, 100.0, null)

        viewModel.startLineConfiguration(product)
        advanceUntilIdle()
        viewModel.confirmLineConfiguration(1.0, 100.0, 1)

        viewModel.startLineConfiguration(product)
        advanceUntilIdle()
        viewModel.confirmLineConfiguration(1.0, 100.0, 16)
        
        whenever(repository.createFactura(any())).thenReturn(Result.success(123L))
        viewModel.proceedToEmission()
        advanceUntilIdle()
        
        val captor = argumentCaptor<FacturaCreateDto>()
        verify(repository).createFactura(captor.capture())
        val payload = captor.firstValue
        
        assertNull(payload.documentoProductos[0].indicadorFacturacionC4)
        assertNull(payload.documentoProductos[1].indicadorFacturacionC4)
        assertEquals(16, payload.documentoProductos[2].indicadorFacturacionC4)
    }

    private suspend fun TestScope.setupViewModelForPayload(): EmissionViewModel {
        val pv = PuntoVentaDto(1, "Main", 1, true, true)
        val item = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")
        whenever(repository.getPuntosVenta()).thenReturn(Result.success(listOf(pv)))
        whenever(repository.getDocumentosHabilitados(any())).thenReturn(Result.success(listOf(CfeFiscalDocumentAvailabilityGroupDto(1, listOf(item)))))
        whenever(repository.getMonedas()).thenReturn(Result.success(listOf(CatalogoItemDto(1, "UYU"))))
        whenever(repository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(1, "A"))))
        whenever(repository.getFormasPago()).thenReturn(Result.success(listOf(CatalogoItemDto(1, "F"))))
        whenever(repository.getListasPrecio()).thenReturn(Result.success(emptyList()))
        whenever(repository.getVencimientos()).thenReturn(Result.success(emptyList()))
        
        val viewModel = EmissionViewModel(repository)
        advanceUntilIdle()
        viewModel.selectPOS(pv)
        advanceUntilIdle()
        viewModel.selectFiscalType(item)
        advanceUntilIdle()
        viewModel.selectedCliente = ClienteDto(1, "C")
        
        return viewModel
    }
}

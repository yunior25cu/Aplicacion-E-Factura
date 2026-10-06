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
            whenever(repository.getPuntosVenta()).thenReturn(Result.success(emptyList()))
            whenever(repository.getDocumentosHabilitados(any())).thenReturn(Result.success(emptyList()))
            whenever(repository.getTasaCambios(any())).thenReturn(Result.success(emptyList()))
            whenever(repository.getIndicadoresFacturacion(any(), any(), anyOrNull(), any())).thenReturn(Result.success(emptyList()))
            whenever(repository.getIndicadorSugerido(any(), any(), anyOrNull(), any(), anyOrNull(), any())).thenReturn(Result.success(CfeFiscalIndicadorSugeridoDto(null, null, false, "N/A")))
            whenever(repository.caePrecheck(any(), any(), anyOrNull(), any())).thenReturn(Result.success(CaePrecheckResultDto(true)))
            whenever(repository.validateCfe(any(), any(), any(), any())).thenReturn(Result.success(CfeValidateResponseDto(true)))
            whenever(repository.emitCfe(any(), any())).thenReturn(Result.success(CfeEmitResponse("req-123", "url")))
            whenever(repository.getCfeStatus(any(), anyOrNull())).thenReturn(Result.success(CfeStatusResponse(123L, 1, "OK")))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load fetches points of sale and auto-selects if available`() = runTest {
        val pvs = listOf(PuntoVentaDto(1, "Main", 1, activo = true, esPredeterminado = false))
        val items = listOf(CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A"))
        
        whenever(repository.getPuntosVenta()).thenReturn(Result.success(pvs))
        whenever(repository.getDocumentosHabilitados(1)).thenReturn(Result.success(listOf(CfeFiscalDocumentAvailabilityGroupDto(1, items))))

        val viewModel = EmissionViewModel(repository)
        advanceUntilIdle()
        
        // Debe auto-seleccionar y pasar a SelectType
        assertTrue(viewModel.uiState is EmissionUiState.SelectType)
        assertEquals(items, (viewModel.uiState as EmissionUiState.SelectType).types)
        assertEquals(pvs[0], viewModel.selectedPOS)
    }

    @Test
    fun `proceedToEmission creates electronic draft with correct payload`() = runTest {
        val pv = PuntoVentaDto(1, "Main", 1, true, true)
        val item = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")
        val client = ClienteDto(932, "Test Client")
        val moneda = TasaCambioSimpleDto(1, "UYU", "Peso Uruguayo", "$", 2, 1.0, null)
        val almacen = CatalogoItemDto(10, "Deposito")
        val sugerencia = CfeFiscalIndicadorSugeridoDto(persistedValue = 16, suggestedValue = 16, isAutomatic = true, label = "IVA Minimo")
        
        whenever(repository.getPuntosVenta()).thenReturn(Result.success(listOf(pv)))
        whenever(repository.getDocumentosHabilitados(any())).thenReturn(Result.success(listOf(CfeFiscalDocumentAvailabilityGroupDto(1, listOf(item)))))
        whenever(repository.getTasaCambios(any())).thenReturn(Result.success(listOf(moneda)))
        whenever(repository.getAlmacenes()).thenReturn(Result.success(listOf(almacen)))
        whenever(repository.getListasPrecio()).thenReturn(Result.success(emptyList()))
        whenever(repository.getVencimientos()).thenReturn(Result.success(emptyList()))
        whenever(repository.getIndicadoresFacturacion(any(), any(), anyOrNull(), any())).thenReturn(Result.success(emptyList()))
        whenever(repository.getIndicadorSugerido(any(), any(), anyOrNull(), any(), anyOrNull(), any())).thenReturn(Result.success(sugerencia))

        val viewModel = EmissionViewModel(repository)
        advanceUntilIdle()
        
        viewModel.selectFiscalType(item)
        advanceUntilIdle()
        
        viewModel.selectedCliente = client
        viewModel.selectedMoneda = moneda
        viewModel.selectedAlmacen = almacen
        viewModel.selectedCondicionPago = CondicionPagoComercial.CREDITO
        
        val product = ProductoDto(1001, "Product", "P001", 100.0, 0.22)
        viewModel.startLineConfiguration(product)
        advanceUntilIdle()
        
        viewModel.confirmLineConfiguration(1.0, 100.0, viewModel.lineConfigurationSugerido?.persistedValue, viewModel.lineConfigurationSugerido?.suggestedValue, viewModel.lineConfigurationSugerido?.label)
        advanceUntilIdle()
        
        whenever(repository.createFacturaElectronicDraft(any())).thenReturn(Result.success(123L))

        viewModel.proceedToEmission()
        advanceUntilIdle()

        val captor = argumentCaptor<FacturaCreateDto>()
        verify(repository).createFacturaElectronicDraft(captor.capture())
        
        val payload = captor.firstValue
        
        // Assertions
        assertEquals(122.0, payload.importeTotalBase, 0.0)
        assertEquals(111, payload.cfeCodeIntent)
        assertEquals(1, payload.puntoVentaFiscalIntentId)
        assertEquals("A", payload.serieFiscalPreferidaIntent)
        assertEquals(2, payload.condicionPagoComercial)
        assertEquals(1, payload.idMoneda)
        assertEquals(1.0, payload.tasaCambio, 0.0)
    }

    @Test
    fun `currency selection updates exchange rate`() = runTest {
        val viewModel = setupViewModelForPayload()
        val usd = TasaCambioSimpleDto(2, "USD", "Dolar", "U\$S", 2, 40.0, null)
        
        viewModel.selectedMoneda = usd
        
        val product = ProductoDto(1001, "Product", "P001", 100.0, 0.0)
        viewModel.startLineConfiguration(product)
        advanceUntilIdle()
        viewModel.confirmLineConfiguration(1.0, 40.0, null) // 40 UYU = 1 USD
        
        whenever(repository.createFacturaElectronicDraft(any())).thenReturn(Result.success(123L))
        viewModel.proceedToEmission()
        advanceUntilIdle()
        
        val captor = argumentCaptor<FacturaCreateDto>()
        verify(repository).createFacturaElectronicDraft(captor.capture())
        val payload = captor.firstValue
        
        assertEquals(40.0, payload.tasaCambio, 0.0)
        assertEquals(40.0, payload.importeTotalBase, 0.0)
        assertEquals(1.0, payload.importeTotalOriginal, 0.0) 
    }

    private suspend fun TestScope.setupViewModelForPayload(): EmissionViewModel {
        val pv = PuntoVentaDto(1, "Main", 1, true, true)
        val item = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")
        val monedaBase = TasaCambioSimpleDto(1, "UYU", "Peso", "$", 2, 1.0, null)
        
        whenever(repository.getPuntosVenta()).thenReturn(Result.success(listOf(pv)))
        whenever(repository.getDocumentosHabilitados(any())).thenReturn(Result.success(listOf(CfeFiscalDocumentAvailabilityGroupDto(1, listOf(item)))))
        whenever(repository.getTasaCambios(any())).thenReturn(Result.success(listOf(monedaBase)))
        whenever(repository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(1, "A"))))
        whenever(repository.getListasPrecio()).thenReturn(Result.success(emptyList()))
        whenever(repository.getVencimientos()).thenReturn(Result.success(emptyList()))
        
        val viewModel = EmissionViewModel(repository)
        advanceUntilIdle()
        viewModel.selectFiscalType(item)
        advanceUntilIdle()
        viewModel.selectedCliente = ClienteDto(1, "C")
        
        return viewModel
    }
}

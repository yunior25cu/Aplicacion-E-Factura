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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class EmissionFiscalLogicTest {

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
            whenever(repository.emitCfe(any(), any())).thenReturn(Result.success(CfeEmitResponse("req-123", "url")))
            whenever(repository.getCfeStatus(any(), anyOrNull())).thenReturn(Result.success(CfeStatusResponse(123L, 1, "OK")))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.setupViewModel(): EmissionViewModel {
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

    @Test
    fun `cancelLineConfiguration resets state`() = runTest {
        val viewModel = setupViewModel()
        val product = ProductoDto(1001, "Product", "P001", 100.0, 0.22)
        viewModel.startLineConfiguration(product)
        advanceUntilIdle()
        
        viewModel.cancelLineConfiguration()
        
        assertFalse(viewModel.isConfiguringLine)
        assertNull(viewModel.productBeingConfigured)
        assertNull(viewModel.lineConfigurationSugerido)
    }

    @Test
    fun `proceedToEmission fails if cae precheck is invalid`() = runTest {
        val viewModel = setupViewModel()
        
        whenever(repository.caePrecheck(any(), any(), anyOrNull(), any())).thenReturn(Result.success(CaePrecheckResultDto(false, "Vencido")))
        
        val product = ProductoDto(1001, "Product", "P001", 100.0, 0.22)
        viewModel.startLineConfiguration(product)
        advanceUntilIdle()
        viewModel.confirmLineConfiguration(1.0, 100.0, null, null, null)
        
        viewModel.proceedToEmission()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState is EmissionUiState.Error)
        val error = (viewModel.uiState as EmissionUiState.Error).error
        assertTrue(error is AppError.Validation)
        assertEquals("Vencido", error.message)
    }

    @Test
    fun `proceedToEmission fails if validateCfe is invalid`() = runTest {
        val viewModel = setupViewModel()
        
        whenever(repository.createFactura(any())).thenReturn(Result.success(123L))
        whenever(repository.validateCfe(any(), any(), any(), any())).thenReturn(Result.success(CfeValidateResponseDto(false, listOf("Error 1"))))
        
        val product = ProductoDto(1001, "Product", "P001", 100.0, 0.22)
        viewModel.startLineConfiguration(product)
        advanceUntilIdle()
        viewModel.confirmLineConfiguration(1.0, 100.0, null, null, null)
        
        viewModel.proceedToEmission()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState is EmissionUiState.Error)
        val error = (viewModel.uiState as EmissionUiState.Error).error
        assertTrue(error is AppError.Validation)
        assertTrue(error.message?.contains("Error 1") == true)
    }

    @Test
    fun `proceedToEmission polls with statusUrl`() = runTest {
        val viewModel = setupViewModel()
        
        whenever(repository.createFactura(any())).thenReturn(Result.success(123L))
        
        val product = ProductoDto(1001, "Product", "P001", 100.0, 0.22)
        viewModel.startLineConfiguration(product)
        advanceUntilIdle()
        viewModel.confirmLineConfiguration(1.0, 100.0, null, null, null)
        
        viewModel.proceedToEmission()
        advanceUntilIdle()
        
        verify(repository).emitCfe(eq(123L), any())
        verify(repository).getCfeStatus(eq(123L), eq("url"))
    }
}

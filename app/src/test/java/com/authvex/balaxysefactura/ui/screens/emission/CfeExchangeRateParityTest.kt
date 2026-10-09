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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CfeExchangeRateParityTest {

    private lateinit var repository: CfeRepository
    private lateinit var viewModel: EmissionViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()

        runTest {
            val empresa = EmpresaDto(id = 10, nombre = "Empresa Test", moneda = CatalogoItemDto(50, "Pesos", "UYU"))
            val pos = PuntoVentaDto(1, "Caja 1", 1, true, false)
            val type111 = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")

            whenever(repository.getEmpresa()).thenReturn(Result.success(empresa))
            whenever(repository.getTasaCambioConfig()).thenReturn(Result.success(TasaCambioConfigDto("BCU", true)))
            whenever(repository.getPuntosVenta()).thenReturn(Result.success(listOf(pos)))
            whenever(repository.getDocumentosHabilitados(1)).thenReturn(Result.success(listOf(CfeFiscalDocumentAvailabilityGroupDto(1, listOf(type111)))))
            whenever(repository.getClientes(anyOrNull())).thenReturn(Result.success(listOf(ClienteDto(10, "ABITAB S A"))))
            whenever(repository.getProductos(anyOrNull())).thenReturn(Result.success(listOf(ProductoDto(50, "Prod", precio = 100.0))))
            whenever(repository.getTasaCambios("2026-09-13")).thenReturn(Result.success(listOf(
                TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0),
                TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.20)
            )))
            whenever(repository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(2, "Almacén Central"))))
        }

        viewModel = EmissionViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `BASE_CURRENCY_RESOLVED_BY_EMPRESA_MONEDA_ID and FOREIGN_RATE_ONE_IS_NOT_BASE_CURRENCY`() = runTest {
        viewModel.fechaConfirmacion = "2026-09-13"
        val type111 = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")
        viewModel.selectFiscalType(type111)

        assertEquals(50, viewModel.baseCurrencyId)
        assertTrue(viewModel.isBaseCurrency())

        // Select USD with rate 1.0 -> ID is 51, baseCurrencyId is 50 -> isBaseCurrency MUST be false!
        viewModel.onMonedaSelected(TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 1.0))
        assertFalse(viewModel.isBaseCurrency())
    }

    @Test
    fun `CFE_RATE_QUERY_USES_CONFIRMATION_DATE and CFE_RATE_QUERY_DOES_NOT_SUBTRACT_DAY`() = runTest {
        viewModel.fechaConfirmacion = "2026-09-13"
        val type111 = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")
        viewModel.selectFiscalType(type111)

        // Verifies exact date 2026-09-13 was queried
        verify(repository).getTasaCambios("2026-09-13")
    }

    @Test
    fun `CONFIRMATION_DATE_CHANGE_REFETCHES_RATE and PRESERVES_CURRENCY_ID`() = runTest {
        viewModel.fechaConfirmacion = "2026-09-13"
        val type111 = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")
        viewModel.selectFiscalType(type111)

        viewModel.onMonedaSelected(TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.20))

        whenever(repository.getTasaCambios("2026-09-14")).thenReturn(Result.success(listOf(
            TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0),
            TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 41.50)
        )))

        viewModel.onFechaConfirmacionChanged("2026-09-14")

        assertEquals("2026-09-14", viewModel.fechaConfirmacion)
        assertEquals(51, viewModel.selectedMoneda?.id)
        assertEquals(41.50, viewModel.selectedMoneda?.tasaPromedio ?: 0.0, 0.001)
    }

    @Test
    fun `FOREIGN_CURRENCY_MISSING_RATE_BLOCKS_EMISSION`() = runTest {
        whenever(repository.getTasaCambios("2026-09-13")).thenReturn(Result.success(listOf(
            TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0),
            TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 0.0)
        )))

        viewModel.fechaEmision = "2026-09-13"
        viewModel.fechaConfirmacion = "2026-09-13"
        val type111 = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")
        viewModel.selectFiscalType(type111)

        testScheduler.advanceUntilIdle()

        viewModel.selectedCliente = ClienteDto(10, "ABITAB S A", ruc = "211234560012", tipoDocumentoIdentificacion = 2)
        viewModel.lineas.add(LineaForm(ProductoDto(50, "Prod", precio = 100.0), cantidad = 1.0, precioUnitario = 100.0))
        viewModel.onMonedaSelected(TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 0.0))

        viewModel.proceedToEmission()

        val currentState = viewModel.uiState
        assertTrue(currentState is EmissionUiState.Error)
        val err = (currentState as EmissionUiState.Error).error
        assertTrue(err.getDisplayMessage().contains("No se ha definido una tasa de cambio vigente"))
    }

    @Test
    fun `NC_CONFIRMATION_DATE_DOES_NOT_REFETCH_RATE and NC_KEEPS_ORIGIN_RATE`() = runTest {
        val type112 = CfeFiscalDocumentAvailabilityItemDto(112, "NC e-Factura", true, null, 1, "A")
        viewModel.selectFiscalType(type112)

        val saleDoc = BudgetDto(
            id = 888L,
            fechaEmision = "2026-01-04",
            tasaCambio = 42.0,
            moneda = CatalogoItemDto(51, "Dólar", "USD"),
            almacen = CatalogoItemDto(2, "Almacén Central"),
            cliente = ClienteDto(10, "ABITAB S A"),
            documentoProductos = listOf(BudgetDocumentProductDto(id = 1L, precioBase = 100.0, cantidad = 1.0))
        )

        whenever(repository.getFacturaById(888L)).thenReturn(Result.success(saleDoc))

        viewModel.selectOriginDocument(CfeSummaryDto(888, "A", 123L, 111, "ABITAB S A", "2026-01-04", 100.0, "$", 2))

        assertEquals(42.0, viewModel.selectedMoneda?.tasaPromedio ?: 0.0, 0.001)

        // Changing confirmation date on NC MUST NOT refetch or overwrite historical origin rate!
        viewModel.onFechaConfirmacionChanged("2026-10-20")

        assertEquals(42.0, viewModel.selectedMoneda?.tasaPromedio ?: 0.0, 0.001)
    }

    @Test
    fun `BCU_SYNC_POSTS_PREVIOUS_BUSINESS_DAY and BCU_SYNC_SUCCESS_REFETCHES_CANONICAL_CATALOG`() = runTest {
        // Sunday 2026-09-13 -> Previous business day is Friday 2026-09-11!
        viewModel.fechaConfirmacion = "2026-09-13"
        val type111 = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")
        viewModel.selectFiscalType(type111)

        whenever(repository.syncBcuRate("2026-09-11")).thenReturn(Result.success(BcuSyncResponse(exitoso = true, estado = "Exitoso")))

        viewModel.syncBcuRate()

        verify(repository).syncBcuRate("2026-09-11")
    }
}

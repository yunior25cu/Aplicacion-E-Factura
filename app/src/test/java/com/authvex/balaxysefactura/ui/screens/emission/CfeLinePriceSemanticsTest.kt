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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CfeLinePriceSemanticsTest {

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
            whenever(repository.getProductos(anyOrNull())).thenReturn(Result.success(listOf(ProductoDto(50, "Prod", precio = 500.0, tasaIva = 0.22))))
            whenever(repository.getTasaCambios("2026-09-13")).thenReturn(Result.success(listOf(
                TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0),
                TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.0)
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
    fun `BASE_PRICE_INPUT_IS_BASE_CURRENCY - UYU price input 10 results in precioBase 10 and precioOriginal 0`() = runTest {
        viewModel.fechaConfirmacion = "2026-09-13"
        val type111 = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")
        viewModel.selectFiscalType(type111)

        viewModel.selectedCliente = ClienteDto(10, "ABITAB S A", ruc = "211234560012", tipoDocumentoIdentificacion = 2)
        viewModel.selectedMoneda = TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0)
        viewModel.preciosIncluyenIva = false

        viewModel.lineas.add(LineaForm(ProductoDto(50, "Prod", precio = 500.0, tasaIva = 0.0), cantidad = 1.0, precioUnitario = 10.0))

        whenever(repository.caePrecheck(any(), any(), anyOrNull(), any())).thenReturn(Result.success(CaePrecheckResultDto(hasValidCae = true, message = "OK")))
        whenever(repository.validateCfe(any(), any(), any(), anyOrNull())).thenReturn(Result.success(CfeValidateResponseDto(isValid = true)))
        whenever(repository.emitCfe(any(), any())).thenReturn(Result.success(CfeEmitResponse("1", null)))

        whenever(repository.createFacturaElectronicDraft(check { request ->
            assertEquals(10.0, request.importeBase, 0.001)
            assertEquals(0.0, request.importeOriginal, 0.001)
            assertEquals(10.0, request.documentoProductos.first().precioBase, 0.001)
            assertEquals(0.0, request.documentoProductos.first().precioOriginal, 0.001)
        })).thenReturn(Result.success(999L))

        viewModel.proceedToEmission()
    }

    @Test
    fun `FOREIGN_PRICE_INPUT_IS_ORIGINAL_CURRENCY and FOREIGN_PRICE_IS_NOT_DIVIDED_BY_RATE`() = runTest {
        viewModel.fechaConfirmacion = "2026-09-13"
        val type111 = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")
        viewModel.selectFiscalType(type111)

        viewModel.selectedCliente = ClienteDto(10, "ABITAB S A", ruc = "211234560012", tipoDocumentoIdentificacion = 2)
        viewModel.onMonedaSelected(TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.0))
        viewModel.preciosIncluyenIva = false

        // User typed USD 10.0
        viewModel.lineas.add(LineaForm(ProductoDto(50, "Prod", precio = 500.0, tasaIva = 0.0), cantidad = 2.0, precioUnitario = 10.0))

        whenever(repository.caePrecheck(any(), any(), anyOrNull(), any())).thenReturn(Result.success(CaePrecheckResultDto(hasValidCae = true, message = "OK")))
        whenever(repository.validateCfe(any(), any(), any(), anyOrNull())).thenReturn(Result.success(CfeValidateResponseDto(isValid = true)))
        whenever(repository.emitCfe(any(), any())).thenReturn(Result.success(CfeEmitResponse("1", null)))

        whenever(repository.createFacturaElectronicDraft(check { request ->
            // Original USD: qty 2 * 10 = 20.0
            assertEquals(20.0, request.importeOriginal, 0.001)
            assertEquals(10.0, request.documentoProductos.first().precioOriginal, 0.001)
            assertNotEquals(0.25, request.documentoProductos.first().precioOriginal, 0.001)

            // Base UYU: 20 * 40 = 800.0
            assertEquals(800.0, request.importeBase, 0.001)
            assertEquals(400.0, request.documentoProductos.first().precioBase, 0.001)
        })).thenReturn(Result.success(999L))

        viewModel.proceedToEmission()
    }

    @Test
    fun `FOREIGN_NET_PRICE_CALCULATION - USD rate 40 qty 2 price 10 tax 22 percent net mode`() = runTest {
        viewModel.fechaConfirmacion = "2026-09-13"
        val type111 = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")
        viewModel.selectFiscalType(type111)

        viewModel.selectedCliente = ClienteDto(10, "ABITAB S A", ruc = "211234560012", tipoDocumentoIdentificacion = 2)
        viewModel.onMonedaSelected(TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.0))
        viewModel.preciosIncluyenIva = false

        viewModel.lineas.add(LineaForm(ProductoDto(50, "Prod", precio = 500.0, tasaIva = 0.22), cantidad = 2.0, precioUnitario = 10.0))

        whenever(repository.caePrecheck(any(), any(), anyOrNull(), any())).thenReturn(Result.success(CaePrecheckResultDto(hasValidCae = true, message = "OK")))
        whenever(repository.validateCfe(any(), any(), any(), anyOrNull())).thenReturn(Result.success(CfeValidateResponseDto(isValid = true)))
        whenever(repository.emitCfe(any(), any())).thenReturn(Result.success(CfeEmitResponse("1", null)))

        whenever(repository.createFacturaElectronicDraft(check { request ->
            val line = request.documentoProductos.first()

            // Original USD: price 10, amount 20, iva 4.40, total 24.40
            assertEquals(10.0, line.precioOriginal, 0.001)
            assertEquals(20.0, line.importeOriginal, 0.001)
            assertEquals(4.40, line.ivaOriginal, 0.001)
            assertEquals(24.40, line.importeOriginalConIva, 0.001)

            // Base UYU (* 40): price 400, amount 800, iva 176, total 976
            assertEquals(400.0, line.precioBase, 0.001)
            assertEquals(800.0, line.importeBase, 0.001)
            assertEquals(176.0, line.iva, 0.001)
            assertEquals(976.0, line.importeBaseConIva, 0.001)

            // Header totals
            assertEquals(20.0, request.importeOriginal, 0.001)
            assertEquals(4.40, request.ivaOriginal, 0.001)
            assertEquals(24.40, request.importeTotalOriginal, 0.001)

            assertEquals(800.0, request.importeBase, 0.001)
            assertEquals(176.0, request.iva, 0.001)
            assertEquals(976.0, request.importeTotalBase, 0.001)
        })).thenReturn(Result.success(999L))

        viewModel.proceedToEmission()
    }

    @Test
    fun `FOREIGN_GROSS_PRICE_CALCULATION - USD rate 40 qty 1 gross price 12_20 tax 22 percent`() = runTest {
        viewModel.fechaConfirmacion = "2026-09-13"
        val type111 = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")
        viewModel.selectFiscalType(type111)

        viewModel.selectedCliente = ClienteDto(10, "ABITAB S A", ruc = "211234560012", tipoDocumentoIdentificacion = 2)
        viewModel.onMonedaSelected(TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.0))
        viewModel.preciosIncluyenIva = true

        viewModel.lineas.add(LineaForm(ProductoDto(50, "Prod", precio = 500.0, tasaIva = 0.22), cantidad = 1.0, precioUnitario = 12.20))

        whenever(repository.caePrecheck(any(), any(), anyOrNull(), any())).thenReturn(Result.success(CaePrecheckResultDto(hasValidCae = true, message = "OK")))
        whenever(repository.validateCfe(any(), any(), any(), anyOrNull())).thenReturn(Result.success(CfeValidateResponseDto(isValid = true)))
        whenever(repository.emitCfe(any(), any())).thenReturn(Result.success(CfeEmitResponse("1", null)))

        whenever(repository.createFacturaElectronicDraft(check { request ->
            val line = request.documentoProductos.first()

            // Original USD: gross 12.20, net 10.00, iva 2.20
            assertEquals(10.0, line.importeOriginal, 0.01)
            assertEquals(2.20, line.ivaOriginal, 0.01)
            assertEquals(12.20, line.importeOriginalConIva, 0.01)

            // Base UYU (* 40): gross 488.0, net 400.0, iva 88.0
            assertEquals(400.0, line.importeBase, 0.01)
            assertEquals(88.0, line.iva, 0.01)
            assertEquals(488.0, line.importeBaseConIva, 0.01)
        })).thenReturn(Result.success(999L))

        viewModel.proceedToEmission()
    }

    @Test
    fun `CURRENCY_CHANGE_CLEARS_LINE_PRICE - changing currency resets line prices to zero`() = runTest {
        viewModel.onMonedaSelected(TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0))
        viewModel.lineas.add(LineaForm(ProductoDto(50, "Prod", precio = 500.0), cantidad = 3.0, precioUnitario = 500.0))

        // Change currency to USD
        viewModel.onMonedaSelected(TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.0))

        // Product and quantity remain, but unit price is reset to 0.0 for user input in new currency
        assertEquals(1, viewModel.lineas.size)
        assertEquals(50, viewModel.lineas.first().producto.id)
        assertEquals(3.0, viewModel.lineas.first().cantidad, 0.001)
        assertEquals(0.0, viewModel.lineas.first().precioUnitario, 0.001)
    }

    @Test
    fun `FOREIGN_PRODUCT_SELECTION_DOES_NOT_PREFILL_BASE_PRICE - line configurator in USD starts with price zero`() = runTest {
        viewModel.baseCurrencyId = 50
        viewModel.onMonedaSelected(TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.0))

        val prodUYU = ProductoDto(50, "Prod UYU", precio = 500.0)
        viewModel.startLineConfiguration(prodUYU)

        assertEquals("0", viewModel.dialogUnitPriceText)
        assertNotEquals("500.0", viewModel.dialogUnitPriceText)
    }

    @Test
    fun `FOREIGN_RATE_CHANGE_PRESERVES_ORIGINAL_PRICE - date rate change preserves original USD 10 price and updates base UYU`() = runTest {
        viewModel.onMonedaSelected(TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.0))
        viewModel.lineas.add(LineaForm(ProductoDto(50, "Prod", precio = 500.0, tasaIva = 0.0), cantidad = 1.0, precioUnitario = 10.0))

        // Change rate for new date to 41.0
        whenever(repository.getTasaCambios("2026-09-14")).thenReturn(Result.success(listOf(
            TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0),
            TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 41.0)
        )))

        viewModel.onFechaConfirmacionChanged("2026-09-14")

        // Original typed price remains 10.0 USD
        assertEquals(10.0, viewModel.lineas.first().precioUnitario, 0.001)

        whenever(repository.caePrecheck(any(), any(), anyOrNull(), any())).thenReturn(Result.success(CaePrecheckResultDto(hasValidCae = true, message = "OK")))
        whenever(repository.validateCfe(any(), any(), any(), anyOrNull())).thenReturn(Result.success(CfeValidateResponseDto(isValid = true)))
        whenever(repository.emitCfe(any(), any())).thenReturn(Result.success(CfeEmitResponse("1", null)))

        whenever(repository.createFacturaElectronicDraft(check { request ->
            assertEquals(10.0, request.documentoProductos.first().precioOriginal, 0.001)
            assertEquals(410.0, request.documentoProductos.first().precioBase, 0.001) // 10 * 41 = 410
        })).thenReturn(Result.success(999L))

        viewModel.selectedCliente = ClienteDto(10, "ABITAB S A", ruc = "211234560012", tipoDocumentoIdentificacion = 2)
        viewModel.selectedPOS = PuntoVentaDto(1, "Caja 1", 1, true, false)
        viewModel.selectedFiscalType = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")

        viewModel.proceedToEmission()
    }
}

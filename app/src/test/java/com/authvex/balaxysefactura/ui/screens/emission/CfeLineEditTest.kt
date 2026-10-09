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
class CfeLineEditTest {

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
            whenever(repository.getClientes(anyOrNull())).thenReturn(Result.success(listOf(ClienteDto(10, "ABITAB S A"))))
            whenever(repository.getProductos(anyOrNull())).thenReturn(Result.success(listOf(ProductoDto(50, "Suscripción Mensual - Plan Especial (Promocional)", precio = 480.0, tasaIva = 0.0))))
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
    fun `CFE_LINE_EDIT_ICON_OPENS_DIALOG and CFE_LINE_EDIT_DIALOG_USES_CURRENT_QUANTITY_AND_PRICE`() = runTest {
        val prod = ProductoDto(50, "Suscripción Mensual - Plan Especial (Promocional)", precio = 480.0, tasaIva = 0.0)
        viewModel.lineas.add(LineaForm(prod, cantidad = 1.0, precioUnitario = 480.0, indicadorFacturacionC4 = 1))

        viewModel.openLineEditDialog(0)

        assertEquals(0, viewModel.editingLineIndex)
        assertTrue(viewModel.isConfiguringLine)
        assertEquals("1.0", viewModel.dialogQuantityText)
        assertEquals("480.0", viewModel.dialogUnitPriceText)
        assertEquals("Suscripción Mensual - Plan Especial (Promocional)", viewModel.productBeingConfigured?.nombre)
    }

    @Test
    fun `CFE_LINE_EDIT_CANCEL_DOES_NOT_CHANGE_LINE - canceling edit preserves original quantity and price`() = runTest {
        val prod = ProductoDto(50, "Prod 1", precio = 100.0, tasaIva = 0.22)
        viewModel.lineas.add(LineaForm(prod, cantidad = 1.0, precioUnitario = 100.0))

        viewModel.openLineEditDialog(0)
        viewModel.dialogQuantityText = "10.0"
        viewModel.dialogUnitPriceText = "999.0"

        viewModel.cancelLineConfiguration()

        assertNull(viewModel.editingLineIndex)
        assertFalse(viewModel.isConfiguringLine)
        assertEquals(1.0, viewModel.lineas.first().cantidad, 0.001)
        assertEquals(100.0, viewModel.lineas.first().precioUnitario, 0.001)
    }

    @Test
    fun `CFE_LINE_EDIT_CONFIRM_UPDATES_QUANTITY_AND_PRICE_AND_RECALCULATES_TOTAL`() = runTest {
        val prod = ProductoDto(50, "Suscripción Mensual - Plan Especial (Promocional)", precio = 480.0, tasaIva = 0.0)
        viewModel.lineas.add(LineaForm(prod, cantidad = 1.0, precioUnitario = 480.0, indicadorFacturacionC4 = 3))

        viewModel.openLineEditDialog(0)

        // Operator changes: Cantidad = 2, Precio = 500.00
        val success = viewModel.confirmLineEdit(quantityStr = "2", priceStr = "500.00")

        assertTrue(success)
        assertNull(viewModel.editingLineIndex)

        val updatedLine = viewModel.lineas.first()
        assertEquals(2.0, updatedLine.cantidad, 0.001)
        assertEquals(500.0, updatedLine.precioUnitario, 0.001)
        assertEquals(50, updatedLine.producto.id) // Product ID preserved
        assertEquals(3, updatedLine.indicadorFacturacionC4) // C4 metadata preserved
    }

    @Test
    fun `CFE_LINE_EDIT_CONFIRM_UPDATES_ONLY_SELECTED_LINE - index 0 edit does not alter index 1`() = runTest {
        val prod1 = ProductoDto(50, "Prod 1", precio = 100.0)
        val prod2 = ProductoDto(60, "Prod 2", precio = 200.0)

        viewModel.lineas.add(LineaForm(prod1, cantidad = 1.0, precioUnitario = 100.0))
        viewModel.lineas.add(LineaForm(prod2, cantidad = 3.0, precioUnitario = 200.0))

        viewModel.openLineEditDialog(0)
        viewModel.confirmLineEdit(quantityStr = "5.0", priceStr = "150.0")

        assertEquals(5.0, viewModel.lineas[0].cantidad, 0.001)
        assertEquals(150.0, viewModel.lineas[0].precioUnitario, 0.001)

        // Line index 1 MUST remain unchanged
        assertEquals(3.0, viewModel.lineas[1].cantidad, 0.001)
        assertEquals(200.0, viewModel.lineas[1].precioUnitario, 0.001)
        assertEquals(60, viewModel.lineas[1].producto.id)
    }

    @Test
    fun `CFE_REQUEST_STRUCTURE_UNCHANGED - DTO submission preserves structure and uses updated line prices`() = runTest {
        val pos = PuntoVentaDto(1, "Caja 1", 1, true, false)
        val type = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")

        whenever(repository.getPuntosVenta()).thenReturn(Result.success(listOf(pos)))
        whenever(repository.getDocumentosHabilitados(1)).thenReturn(Result.success(listOf(CfeFiscalDocumentAvailabilityGroupDto(1, listOf(type)))))
        whenever(repository.getClientes(anyOrNull())).thenReturn(Result.success(listOf(ClienteDto(10, "ABITAB S A", ruc = "211234560012"))))
        whenever(repository.getTasaCambios(any())).thenReturn(Result.success(listOf(TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0))))
        whenever(repository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(2, "Almacén Central"))))
        whenever(repository.caePrecheck(any(), any(), anyOrNull(), any())).thenReturn(Result.success(CaePrecheckResultDto(hasValidCae = true, message = "OK")))
        whenever(repository.validateCfe(any(), any(), any(), anyOrNull())).thenReturn(Result.success(CfeValidateResponseDto(isValid = true)))
        whenever(repository.emitCfe(any(), any())).thenReturn(Result.success(CfeEmitResponse("1", null)))

        viewModel.selectedCliente = ClienteDto(10, "ABITAB S A", ruc = "211234560012")
        viewModel.selectedAlmacen = CatalogoItemDto(2, "Almacén Central")
        viewModel.selectedMoneda = TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0)
        viewModel.selectedFiscalType = type
        viewModel.selectedPOS = pos

        val prod = ProductoDto(50, "Prod 1", precio = 100.0, tasaIva = 0.0)
        viewModel.lineas.add(LineaForm(prod, cantidad = 1.0, precioUnitario = 100.0))

        // Edit line 0 -> Cantidad 2, Precio 500.00
        viewModel.openLineEditDialog(0)
        viewModel.confirmLineEdit("2.0", "500.00")

        whenever(repository.createFacturaElectronicDraft(check { request ->
            assertEquals(1000.0, request.importeBase, 0.01) // 2 * 500.00 = 1000.0
            assertEquals(1, request.documentoProductos.size)
            assertEquals(2.0, request.documentoProductos.first().cantidad, 0.001)
            assertEquals(500.0, request.documentoProductos.first().precioBase, 0.001)
        })).thenReturn(Result.success(999L))

        viewModel.proceedToEmission()
    }
}

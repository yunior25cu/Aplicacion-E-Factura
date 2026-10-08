package com.authvex.balaxysefactura.ui.screens.budget

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.BudgetRepository
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.ui.screens.budget.form.BudgetFormMode
import com.authvex.balaxysefactura.ui.screens.budget.form.BudgetFormUiState
import com.authvex.balaxysefactura.ui.screens.budget.form.BudgetFormViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetFormViewModelTest {

    private lateinit var budgetRepository: BudgetRepository
    private lateinit var cfeRepository: CfeRepository
    private lateinit var viewModel: BudgetFormViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        budgetRepository = mock()
        cfeRepository = mock()

        runTest {
            whenever(cfeRepository.getEmpresa()).thenReturn(Result.success(EmpresaDto(id = 10, moneda = CatalogoItemDto(50, "Pesos Test", "UYU"))))
            whenever(cfeRepository.getClientes(anyOrNull())).thenReturn(Result.success(emptyList()))
            whenever(cfeRepository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(2, "Almacén Central"))))
            whenever(cfeRepository.getTasaCambios(any())).thenReturn(Result.success(listOf(
                TasaCambioSimpleDto(50, "UYU", "Pesos Test", "$", 2, 1.0),
                TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.0)
            )))
            whenever(cfeRepository.getProductos(anyOrNull())).thenReturn(Result.success(emptyList()))
        }

        viewModel = BudgetFormViewModel(budgetRepository, cfeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `REAL_GET_BUDGET_DESERIALIZATION - deserializes real GET response with base price 1029 and original price 0`() {
        val rawJson = """
            {
              "id": 105,
              "folio": "PF-4/01/2026",
              "numero": 4,
              "fechaEmision": "2026-01-04",
              "fechaConfirmacion": "2026-01-04",
              "fechaVencimiento": "2026-02-04",
              "preciosIncluyenIva": true,
              "tasaCambio": 1.0,
              "importeBase": 1029.0,
              "iva": 0.0,
              "importeTotalBase": 1029.0,
              "importeOriginal": 0.0,
              "ivaOriginal": 0.0,
              "importeTotalOriginal": 0.0,
              "estado": 1,
              "moneda": { "id": 50, "nombre": "Pesos Test", "codigo": "UYU" },
              "almacen": { "id": 2, "nombre": "Almacén Central" },
              "cliente": { "id": 10, "nombre": "ABITAB S A" },
              "documentoProductos": [
                {
                  "id": 1001,
                  "idProducto": 50,
                  "cantidad": 1.0,
                  "precioBase": 1029.0,
                  "importeBase": 1029.0,
                  "iva": 0.0,
                  "precioBaseConIva": 1029.0,
                  "importeBaseConIva": 1029.0,
                  "precioOriginal": 0.0,
                  "importeOriginal": 0.0,
                  "ivaOriginal": 0.0,
                  "precioOriginalConIva": 0.0,
                  "importeOriginalConIva": 0.0,
                  "descripcion": "Suscripción Mensual - Plan Premium",
                  "porcentajeIva": 0.0,
                  "producto": {
                    "id": 50,
                    "denominacion": "Suscripción Mensual - Plan Premium",
                    "precioVenta": 1029.0,
                    "tasaIva": 0.0
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<BudgetDto>(rawJson)

        assertEquals(105L, parsed.id)
        assertEquals(1029.0, parsed.documentoProductos.first().precioBase, 0.001)
        assertEquals(0.0, parsed.documentoProductos.first().precioOriginal, 0.001)
    }

    @Test
    fun `EDIT_NO_CHANGES_PRESERVES_PRICE - base currency budget loads 1029 price and preserves totals`() = runTest {
        val baseCurrencyBudget = BudgetDto(
            id = 105L,
            fechaEmision = "2026-01-04",
            fechaConfirmacion = "2026-01-04",
            fechaVencimiento = "2026-02-04",
            preciosIncluyenIva = true,
            tasaCambio = 1.0,
            importeBase = 1029.0,
            iva = 0.0,
            importeTotalBase = 1029.0,
            importeOriginal = 0.0,
            ivaOriginal = 0.0,
            importeTotalOriginal = 0.0,
            estado = 1,
            moneda = CatalogoItemDto(50, "Pesos Test", "UYU"),
            almacen = CatalogoItemDto(2, "Almacén Central"),
            cliente = ClienteDto(10, "ABITAB S A"),
            documentoProductos = listOf(
                BudgetDocumentProductDto(
                    id = 1001L,
                    idProducto = 50L,
                    cantidad = 1.0,
                    precioBase = 1029.0,
                    importeBase = 1029.0,
                    iva = 0.0,
                    precioBaseConIva = 1029.0,
                    importeBaseConIva = 1029.0,
                    precioOriginal = 0.0,
                    importeOriginal = 0.0,
                    porcentajeIva = 0.0,
                    producto = ProductoDto(50, "Suscripción Mensual - Plan Premium", precio = 1029.0, tasaIva = 0.0)
                )
            )
        )

        whenever(budgetRepository.getBudgetById(105L)).thenReturn(Result.success(baseCurrencyBudget))

        viewModel.loadBudgetForEdit(105L)

        // Line and Document Price Assertions AFTER Fix:
        val line = viewModel.lineItems.value.first()
        assertEquals(1029.0, line.precioUnitario, 0.001)
        assertEquals(1029.0, viewModel.calculateSubtotal(), 0.001)
        assertEquals(0.0, viewModel.calculateIva(), 0.001)
        assertEquals(1029.0, viewModel.calculateTotal(), 0.001)

        whenever(budgetRepository.updateBudget(check { dto ->
            assertEquals(105L, dto.id)
            assertEquals(1029.0, dto.importeBase, 0.01)
            assertEquals(0.0, dto.iva, 0.001)
            assertEquals(1029.0, dto.importeTotalBase, 0.01)

            val updatedLine = dto.documentoProductos.first()
            assertEquals(1029.0, updatedLine.precioBase, 0.01)
            assertEquals(1029.0, updatedLine.importeBase, 0.01)
            assertEquals(0.0, updatedLine.iva, 0.001)
            assertEquals(1029.0, updatedLine.precioBaseConIva, 0.01)
            assertEquals(1029.0, updatedLine.importeBaseConIva, 0.01)
        })).thenReturn(Result.success(Unit))

        viewModel.submitForm()
    }

    @Test
    fun `FOREIGN_EDIT_USES_ORIGINAL_PRICE_FIELDS - foreign currency USD budget loads precioOriginal 100 USD`() = runTest {
        val foreignBudget = BudgetDto(
            id = 202L,
            fechaEmision = "2026-01-04",
            fechaConfirmacion = "2026-01-04",
            fechaVencimiento = "2026-02-04",
            preciosIncluyenIva = false,
            tasaCambio = 40.0,
            importeBase = 4000.0,
            iva = 880.0,
            importeTotalBase = 4880.0,
            importeOriginal = 100.0,
            ivaOriginal = 22.0,
            importeTotalOriginal = 122.0,
            estado = 1,
            moneda = CatalogoItemDto(51, "Dólar", "USD"),
            almacen = CatalogoItemDto(2, "Almacén Central"),
            cliente = ClienteDto(10, "Cliente USD"),
            documentoProductos = listOf(
                BudgetDocumentProductDto(
                    id = 1002L,
                    idProducto = 60L,
                    cantidad = 1.0,
                    precioBase = 4000.0,
                    importeBase = 4000.0,
                    iva = 880.0,
                    precioBaseConIva = 4880.0,
                    importeBaseConIva = 4880.0,
                    precioOriginal = 100.0,
                    importeOriginal = 100.0,
                    ivaOriginal = 22.0,
                    precioOriginalConIva = 122.0,
                    importeOriginalConIva = 122.0,
                    porcentajeIva = 0.22,
                    producto = ProductoDto(60, "Producto USD", precio = 100.0, tasaIva = 0.22)
                )
            )
        )

        whenever(budgetRepository.getBudgetById(202L)).thenReturn(Result.success(foreignBudget))

        viewModel.loadBudgetForEdit(202L)

        val line = viewModel.lineItems.value.first()
        assertEquals(100.0, line.precioUnitario, 0.001)
        assertEquals(100.0, viewModel.calculateSubtotal(), 0.001)
        assertEquals(22.0, viewModel.calculateIva(), 0.001)
        assertEquals(122.0, viewModel.calculateTotal(), 0.001)
    }

    @Test
    fun `NET_22_EDIT - 22 percent net price preserves 200 subtotal 44 iva 244 total`() = runTest {
        val netBudget = BudgetDto(
            id = 301L,
            fechaEmision = "2026-01-04",
            fechaConfirmacion = "2026-01-04",
            fechaVencimiento = "2026-02-04",
            preciosIncluyenIva = false,
            tasaCambio = 1.0,
            importeBase = 200.0,
            iva = 44.0,
            importeTotalBase = 244.0,
            estado = 1,
            moneda = CatalogoItemDto(50, "Pesos Test", "UYU"),
            almacen = CatalogoItemDto(2, "Almacén Central"),
            cliente = ClienteDto(10, "Cliente Net"),
            documentoProductos = listOf(
                BudgetDocumentProductDto(
                    id = 1003L,
                    idProducto = 70L,
                    cantidad = 2.0,
                    precioBase = 100.0,
                    importeBase = 200.0,
                    iva = 44.0,
                    precioBaseConIva = 122.0,
                    importeBaseConIva = 244.0,
                    porcentajeIva = 0.22,
                    producto = ProductoDto(70, "Producto 22% Net", precio = 100.0, tasaIva = 0.22)
                )
            )
        )

        whenever(budgetRepository.getBudgetById(301L)).thenReturn(Result.success(netBudget))

        viewModel.loadBudgetForEdit(301L)

        assertEquals(200.0, viewModel.calculateSubtotal(), 0.01)
        assertEquals(44.0, viewModel.calculateIva(), 0.01)
        assertEquals(244.0, viewModel.calculateTotal(), 0.01)
    }

    @Test
    fun `GROSS_22_EDIT - 22 percent gross price preserves 200 subtotal 44 iva 244 total`() = runTest {
        val grossBudget = BudgetDto(
            id = 302L,
            fechaEmision = "2026-01-04",
            fechaConfirmacion = "2026-01-04",
            fechaVencimiento = "2026-02-04",
            preciosIncluyenIva = true,
            tasaCambio = 1.0,
            importeBase = 200.0,
            iva = 44.0,
            importeTotalBase = 244.0,
            estado = 1,
            moneda = CatalogoItemDto(50, "Pesos Test", "UYU"),
            almacen = CatalogoItemDto(2, "Almacén Central"),
            cliente = ClienteDto(10, "Cliente Gross"),
            documentoProductos = listOf(
                BudgetDocumentProductDto(
                    id = 1004L,
                    idProducto = 70L,
                    cantidad = 2.0,
                    precioBase = 100.0,
                    importeBase = 200.0,
                    iva = 44.0,
                    precioBaseConIva = 122.0,
                    importeBaseConIva = 244.0,
                    porcentajeIva = 0.22,
                    producto = ProductoDto(70, "Producto 22% Gross", precio = 122.0, tasaIva = 0.22)
                )
            )
        )

        whenever(budgetRepository.getBudgetById(302L)).thenReturn(Result.success(grossBudget))

        viewModel.loadBudgetForEdit(302L)

        assertEquals(200.0, viewModel.calculateSubtotal(), 0.01)
        assertEquals(44.0, viewModel.calculateIva(), 0.01)
        assertEquals(244.0, viewModel.calculateTotal(), 0.01)
    }

    @Test
    fun `EDIT_ZERO_COLLAPSE_GUARD - blocks submit when original total was greater than 0 but form calculated total collapses to 0`() = runTest {
        val validBudget = BudgetDto(
            id = 401L,
            importeTotalBase = 1000.0,
            estado = 1,
            moneda = CatalogoItemDto(50, "Pesos Test", "UYU"),
            almacen = CatalogoItemDto(2, "Almacén Central"),
            cliente = ClienteDto(10, "Cliente Test"),
            documentoProductos = listOf(
                BudgetDocumentProductDto(idProducto = 50L, cantidad = 1.0, precioBase = 1000.0, importeBase = 1000.0)
            )
        )

        whenever(budgetRepository.getBudgetById(401L)).thenReturn(Result.success(validBudget))

        viewModel.loadBudgetForEdit(401L)

        // Force zero total on line
        viewModel.lineItems.value = listOf(
            com.authvex.balaxysefactura.ui.screens.budget.form.BudgetFormLineItem(
                producto = ProductoDto(50, "Prod", precio = 0.0),
                cantidad = 1.0,
                precioUnitario = 0.0
            )
        )

        viewModel.submitForm()

        assertTrue(viewModel.uiState is BudgetFormUiState.Error)
        val errMsg = (viewModel.uiState as BudgetFormUiState.Error).message
        assertTrue(errMsg.contains("total cero inesperado"))
        verify(budgetRepository, never()).updateBudget(any())
    }

    @Test
    fun `EDIT_NO_CHANGES_ROUNDTRIP - preserves unexposed fields in UpdateDto without changes`() = runTest {
        val existingBudget = BudgetDto(
            id = 101L,
            fechaEmision = "2026-10-08",
            fechaConfirmacion = "2026-10-08",
            fechaVencimiento = "2026-11-08",
            numeroReferencia = "REF-999",
            nota = "Nota Inalterada",
            terminoCondiciones = "Contado",
            moneda = CatalogoItemDto(50, "Pesos Test", "UYU"),
            tasaCambio = 1.0,
            preciosIncluyenIva = true,
            tipoDescuentoGlobal = 1,
            valorDescuentoGlobal = 5.0,
            almacen = CatalogoItemDto(2, "Almacén Central"),
            cliente = ClienteDto(10, "Cliente Test"),
            centroCosto = CatalogoItemDto(5, "Centro 5"),
            estado = 1, // SinConfirmar
            documentoProductos = listOf(
                BudgetDocumentProductDto(
                    id = 1001L,
                    idProducto = 50L,
                    idSkuVariante = 99L,
                    cantidad = 2.0,
                    precioBase = 50.0,
                    precioBaseConIva = 61.0,
                    importeBase = 100.0,
                    importeBaseConIva = 122.0,
                    precioOriginal = 0.0,
                    descuentoOriginal = 0.0,
                    porcentajeIva = 0.22,
                    indicadorFacturacionC4 = 3,
                    idPromocionSugerida = 77L,
                    descuentoManual = true
                )
            )
        )

        whenever(budgetRepository.getBudgetById(101L)).thenReturn(Result.success(existingBudget))

        viewModel.loadBudgetForEdit(101L)

        assertEquals(BudgetFormMode.EDIT, viewModel.formMode)
        assertEquals(101L, viewModel.editingBudgetId)

        // Preload assertions
        assertEquals("Cliente Test", viewModel.selectedCliente?.nombre)
        assertEquals("Almacén Central", viewModel.selectedAlmacen?.nombre)
        assertEquals(50, viewModel.selectedMoneda?.id)
        assertEquals(1.0, viewModel.tasaCambio, 0.001)
        assertEquals("2026-10-08", viewModel.fechaEmision)
        assertEquals("2026-10-08", viewModel.fechaConfirmacion)
        assertEquals(1, viewModel.lineItems.value.size)

        // Submit form without modifications
        whenever(budgetRepository.updateBudget(check { dto ->
            assertEquals(101L, dto.id)
            assertEquals("2026-10-08", dto.fechaEmision)
            assertEquals("2026-10-08", dto.fechaConfirmacion)
            assertEquals("REF-999", dto.numeroReferencia)
            assertEquals("Nota Inalterada", dto.nota)
            assertEquals(10L, dto.idCliente)
            assertEquals(2L, dto.idAlmacen)
            assertEquals(5L, dto.idCentroCosto)

            // Critical Roundtrip Preservations:
            assertEquals(true, dto.preciosIncluyenIva)
            assertEquals(1, dto.tipoDescuentoGlobal)
            assertEquals(5.0, dto.valorDescuentoGlobal!!, 0.001)

            val line = dto.documentoProductos.first()
            assertEquals(50L, line.idProducto)
            assertEquals(99L, line.idSkuVariante)
            assertEquals(3, line.indicadorFacturacionC4)
            assertEquals(77L, line.idPromocionSugerida)
            assertEquals(true, line.descuentoManual)
            assertEquals(2.0, line.cantidad, 0.001)
        })).thenReturn(Result.success(Unit))

        viewModel.submitForm()
    }

    @Test
    fun `EDIT_CONFIRMED_NOT_AVAILABLE - rejects loading confirmed budget for edit`() = runTest {
        val confirmedBudget = BudgetDto(id = 200L, estado = 2) // Confirmado
        whenever(budgetRepository.getBudgetById(200L)).thenReturn(Result.success(confirmedBudget))

        viewModel.loadBudgetForEdit(200L)

        assertTrue(viewModel.uiState is BudgetFormUiState.Error)
        assertEquals(BudgetFormMode.CREATE, viewModel.formMode)
    }

    @Test
    fun `EDIT_INVOICED_NOT_AVAILABLE - rejects loading invoiced budget for edit`() = runTest {
        val invoicedBudget = BudgetDto(id = 201L, estado = 1, factura = BudgetFacturaDto(id = 99L))
        whenever(budgetRepository.getBudgetById(201L)).thenReturn(Result.success(invoicedBudget))

        viewModel.loadBudgetForEdit(201L)

        assertTrue(viewModel.uiState is BudgetFormUiState.Error)
        assertEquals(BudgetFormMode.CREATE, viewModel.formMode)
    }

    @Test
    fun `EDIT_CHANGE_QUANTITY_SERIALIZES - updating quantity in edit mode serializes new quantity`() = runTest {
        val existingBudget = BudgetDto(
            id = 105L,
            fechaEmision = "2026-10-08",
            fechaConfirmacion = "2026-10-08",
            fechaVencimiento = "2026-11-08",
            moneda = CatalogoItemDto(50, "Pesos Test", "UYU"),
            tasaCambio = 1.0,
            almacen = CatalogoItemDto(2, "Almacén Central"),
            cliente = ClienteDto(10, "Cliente Test"),
            estado = 1,
            documentoProductos = listOf(
                BudgetDocumentProductDto(
                    id = 1001L,
                    idProducto = 50L,
                    cantidad = 2.0,
                    precioBase = 100.0,
                    precioBaseConIva = 122.0,
                    precioOriginal = 0.0,
                    porcentajeIva = 0.22
                )
            )
        )

        whenever(budgetRepository.getBudgetById(105L)).thenReturn(Result.success(existingBudget))
        viewModel.loadBudgetForEdit(105L)

        // Modify line 0 quantity from 2.0 to 5.0
        val prod = viewModel.lineItems.value.first().producto
        viewModel.openLineConfiguration(prod, indexToEdit = 0)
        viewModel.confirmLineConfiguration("5.0", "122.0")

        whenever(budgetRepository.updateBudget(check { dto ->
            assertEquals(105L, dto.id)
            assertEquals(1, dto.documentoProductos.size)
            assertEquals(5.0, dto.documentoProductos.first().cantidad, 0.001)
        })).thenReturn(Result.success(Unit))

        viewModel.submitForm()
    }

    @Test
    fun `TEN_PERCENT_TEST - calculates tax 10 percent`() = runTest {
        viewModel.preciosIncluyenIva = false
        val prod10 = ProductoDto(102, "Producto 10%", precio = 100.0, tasaIva = 0.10)
        viewModel.addLineItem(prod10, cantidad = 2.0)

        assertEquals(200.0, viewModel.calculateSubtotal(), 0.01)
        assertEquals(20.0, viewModel.calculateIva(), 0.01)
        assertEquals(220.0, viewModel.calculateTotal(), 0.01)
    }

    @Test
    fun `TWENTY_TWO_PERCENT_TEST - calculates tax 22 percent`() = runTest {
        viewModel.preciosIncluyenIva = false
        val prod22 = ProductoDto(103, "Producto 22%", precio = 100.0, tasaIva = 0.22)
        viewModel.addLineItem(prod22, cantidad = 2.0)

        assertEquals(200.0, viewModel.calculateSubtotal(), 0.01)
        assertEquals(44.0, viewModel.calculateIva(), 0.01)
        assertEquals(244.0, viewModel.calculateTotal(), 0.01)
    }

    @Test
    fun `BUDGET_DOES_NOT_USE_GENERIC_MONEDA_CATALOG - fetches TasaCambios instead`() = runTest {
        verify(cfeRepository).getTasaCambios(any())
        verify(cfeRepository, never()).getMonedas()
    }

    @Test
    fun `COMPANY_BASE_CURRENCY_SELECTED_BY_ID - matches empresa moneda id`() = runTest {
        assertEquals(50, viewModel.companyBaseCurrencyId)
        assertEquals(50, viewModel.selectedMoneda?.id)
        assertEquals(1.0, viewModel.tasaCambio, 0.001)
    }

    @Test
    fun `DATE_CHANGE_RELOADS_EXCHANGE_RATES - refetches TasaCambios on date change`() = runTest {
        whenever(cfeRepository.getTasaCambios("2026-12-01")).thenReturn(Result.success(listOf(
            TasaCambioSimpleDto(50, "UYU", "Pesos Test", "$", 2, 1.0),
            TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 42.5)
        )))

        viewModel.onFechaConfirmacionChanged("2026-12-01")

        verify(cfeRepository).getTasaCambios("2026-12-01")
    }

    @Test
    fun `PRODUCT_SELECTION_OPENS_LINE_CONFIGURATION - opens dialog with product`() = runTest {
        val prod = ProductoDto(100, "Producto Test", precio = 150.0, tasaIva = 0.22)
        viewModel.openLineConfiguration(prod)

        assertNotNull(viewModel.configuringProduct)
        assertEquals("Producto Test", viewModel.configuringProduct?.nombre)
        assertEquals("1.0", viewModel.dialogQuantityText)
        assertEquals("150.0", viewModel.dialogUnitPriceText)
    }

    @Test
    fun `QUANTITY_DECIMAL_ALLOWED and QUANTITY_CAN_BE_EDITED_AFTER_ADD - recalculates totals`() = runTest {
        val prod = ProductoDto(100, "Producto Test", precio = 100.0, tasaIva = 0.22)
        viewModel.openLineConfiguration(prod)

        val success = viewModel.confirmLineConfiguration(quantityStr = "5.5", priceStr = "100.0")
        assertTrue(success)
        assertEquals(1, viewModel.lineItems.value.size)
        assertEquals(5.5, viewModel.lineItems.value.first().cantidad, 0.001)

        // Totals check
        assertEquals(550.0 / 1.22, viewModel.calculateSubtotal(), 0.01)
        assertEquals(550.0 - (550.0 / 1.22), viewModel.calculateIva(), 0.01)
        assertEquals(550.0, viewModel.calculateTotal(), 0.01)

        // Edit line quantity 5.5 -> 2.5
        viewModel.openLineConfiguration(prod, indexToEdit = 0)
        viewModel.confirmLineConfiguration(quantityStr = "2.5", priceStr = "100.0")

        assertEquals(2.5, viewModel.lineItems.value.first().cantidad, 0.001)
        assertEquals(100.0, viewModel.lineItems.value.first().precioUnitario, 0.001)
        assertEquals(250.0, viewModel.calculateTotal(), 0.01)
    }

    @Test
    fun `QUANTITY_ZERO_BLOCKED and QUANTITY_NEGATIVE_BLOCKED - shows validation error`() = runTest {
        val prod = ProductoDto(100, "Producto Test", precio = 100.0)
        viewModel.openLineConfiguration(prod)

        assertFalse(viewModel.confirmLineConfiguration("0", "100.0"))
        assertNotNull(viewModel.lineDialogError)

        assertFalse(viewModel.confirmLineConfiguration("-2", "100.0"))
        assertNotNull(viewModel.lineDialogError)

        assertFalse(viewModel.confirmLineConfiguration("abc", "100.0"))
        assertNotNull(viewModel.lineDialogError)
    }
}

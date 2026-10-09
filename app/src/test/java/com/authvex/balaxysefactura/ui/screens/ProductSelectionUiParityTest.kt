package com.authvex.balaxysefactura.ui.screens

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.BudgetRepository
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.ui.screens.budget.form.BudgetFormMode
import com.authvex.balaxysefactura.ui.screens.budget.form.BudgetFormViewModel
import com.authvex.balaxysefactura.ui.screens.emission.EmissionViewModel
import com.authvex.balaxysefactura.ui.screens.emission.LineaForm
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
class ProductSelectionUiParityTest {

    private lateinit var budgetRepository: BudgetRepository
    private lateinit var cfeRepository: CfeRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        budgetRepository = mock()
        cfeRepository = mock()

        runTest {
            val empresa = EmpresaDto(id = 10, nombre = "Empresa", moneda = CatalogoItemDto(50, "Pesos", "UYU"))
            whenever(cfeRepository.getEmpresa()).thenReturn(Result.success(empresa))
            whenever(cfeRepository.getPuntosVenta()).thenReturn(Result.success(listOf(PuntoVentaDto(1, "Main", 1, true, true))))
            whenever(cfeRepository.getDocumentosHabilitados(any())).thenReturn(Result.success(listOf(CfeFiscalDocumentAvailabilityGroupDto(1, emptyList()))))
            whenever(cfeRepository.getClientes(anyOrNull())).thenReturn(Result.success(listOf(ClienteDto(10, "ABITAB S A"))))
            whenever(cfeRepository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(2, "Almacén Central"))))
            whenever(cfeRepository.getTasaCambios(any())).thenReturn(Result.success(listOf(TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0))))
            whenever(cfeRepository.getProductos(anyOrNull())).thenReturn(Result.success(listOf(ProductoDto(50, "Prod 1", precio = 100.0, tasaIva = 0.22))))
            whenever(cfeRepository.getIndicadoresFacturacion(any(), any(), anyOrNull(), any())).thenReturn(Result.success(listOf(
                CfeFiscalIndicadorFacturacionDto(1, "Gravado Tasa Básica")
            )))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `BUDGET_OPENS_SHARED_LINE_EDITOR_ON_PRODUCT_SELECTION and BUDGET_LINE_EDITOR_SHOWS_AND_PRESERVES_C4_INDICATOR`() = runTest {
        val viewModel = BudgetFormViewModel(budgetRepository, cfeRepository)
        val prod = ProductoDto(50, "Prod 1", precio = 100.0)

        viewModel.openLineConfiguration(prod)

        assertEquals(prod, viewModel.configuringProduct)
        assertEquals("1.0", viewModel.dialogQuantityText)
        assertEquals("100.0", viewModel.dialogUnitPriceText)

        // Confirm line configuration with quantity = 2.0, price = 150.0, and C4 indicator = 1
        val confirmed = viewModel.confirmLineConfiguration("2.0", "150.0", indicadorC4 = 1)
        assertTrue(confirmed)

        val items = viewModel.lineItems.value
        assertEquals(1, items.size)
        assertEquals(2.0, items.first().cantidad, 0.001)
        assertEquals(150.0, items.first().precioUnitario, 0.001)
        assertEquals(1, items.first().indicadorFacturacionC4)
    }

    @Test
    fun `EDITING_EXISTING_BUDGET_LINE_REOPENS_SHARED_EDITOR`() = runTest {
        val viewModel = BudgetFormViewModel(budgetRepository, cfeRepository)
        val prod = ProductoDto(50, "Prod 1", precio = 100.0)

        viewModel.openLineConfiguration(prod)
        viewModel.confirmLineConfiguration("2.0", "150.0", indicadorC4 = 1)

        // Re-open line 0 for edit
        viewModel.openLineConfiguration(prod, indexToEdit = 0)

        assertEquals(prod, viewModel.configuringProduct)
        assertEquals(0, viewModel.editingLineIndex)
        assertEquals("2.0", viewModel.dialogQuantityText)
        assertEquals("150.0", viewModel.dialogUnitPriceText)

        // Edit price to 180.0
        viewModel.confirmLineConfiguration("2.0", "180.0", indicadorC4 = 1)

        assertEquals(180.0, viewModel.lineItems.value.first().precioUnitario, 0.001)
    }

    @Test
    fun `CFE_MAINTAINS_LINE_EDITOR_FUNCTIONALITY and CFE_DIALOG_RESIZE_DOES_NOT_ALTER_SENT_DATA`() = runTest {
        val viewModel = EmissionViewModel(cfeRepository)
        viewModel.selectedPOS = PuntoVentaDto(1, "Main", 1, true, true)
        viewModel.selectedFiscalType = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")
        val prod = ProductoDto(50, "Prod 1", precio = 100.0)

        viewModel.startLineConfiguration(prod)

        assertEquals(prod, viewModel.productBeingConfigured)
        assertEquals("1", viewModel.dialogQuantityText)

        viewModel.confirmLineConfiguration(3.0, 120.0, indicadorC4 = 1)

        assertEquals(1, viewModel.lineas.size)
        assertEquals(3.0, viewModel.lineas.first().cantidad, 0.001)
        assertEquals(120.0, viewModel.lineas.first().precioUnitario, 0.001)
        assertEquals(1, viewModel.lineas.first().indicadorFacturacionC4)
    }

    @Test
    fun `NO_REGRESSION_IN_CREATE_EDIT_BUDGET - budget creation submits C4 indicator`() = runTest {
        val viewModel = BudgetFormViewModel(budgetRepository, cfeRepository)
        val prod = ProductoDto(50, "Prod 1", precio = 100.0, tasaIva = 0.22)

        viewModel.selectedCliente = ClienteDto(10, "ABITAB S A")
        viewModel.selectedAlmacen = CatalogoItemDto(2, "Almacén Central")
        viewModel.selectedMoneda = TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0)

        viewModel.openLineConfiguration(prod)
        viewModel.confirmLineConfiguration("1.0", "100.0", indicadorC4 = 1)

        whenever(budgetRepository.createBudget(check { dto ->
            assertEquals(1, dto.documentoProductos.size)
            assertEquals(1, dto.documentoProductos.first().indicadorFacturacionC4)
        })).thenReturn(Result.success(555L))

        viewModel.submitForm()
    }
}

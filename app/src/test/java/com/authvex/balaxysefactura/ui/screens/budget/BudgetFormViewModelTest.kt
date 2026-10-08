package com.authvex.balaxysefactura.ui.screens.budget

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.BudgetRepository
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.ui.screens.budget.form.BudgetFormViewModel
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
import org.mockito.kotlin.eq
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

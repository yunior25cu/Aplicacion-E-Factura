package com.authvex.balaxysefactura.ui.screens.budget

import com.authvex.balaxysefactura.core.network.CatalogoItemDto
import com.authvex.balaxysefactura.core.network.ClienteDto
import com.authvex.balaxysefactura.core.network.ProductoDto
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
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
            whenever(cfeRepository.getClientes(anyOrNull())).thenReturn(Result.success(emptyList()))
            whenever(cfeRepository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(2, "Almacén Central"))))
            whenever(cfeRepository.getMonedas()).thenReturn(Result.success(listOf(CatalogoItemDto(1, "Pesos Uruguayos", "UYU"), CatalogoItemDto(2, "Dólares", "USD"))))
            whenever(cfeRepository.getProductos(anyOrNull())).thenReturn(Result.success(emptyList()))
        }

        viewModel = BudgetFormViewModel(budgetRepository, cfeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `BASE_CURRENCY_SEMANTICS_MATCH_WEB - base currency sets original amounts to 0`() = runTest {
        viewModel.selectedCliente = ClienteDto(10, "Cliente Test")
        viewModel.selectedAlmacen = CatalogoItemDto(2, "Almacén")
        viewModel.selectedMoneda = CatalogoItemDto(1, "Pesos Uruguayos", "UYU")
        viewModel.tasaCambio = 1.0
        viewModel.preciosIncluyenIva = true

        val prod = ProductoDto(50, "Producto Base", precio = 122.0, tasaIva = 0.22)
        viewModel.addLineItem(prod, cantidad = 1.0)

        whenever(budgetRepository.createBudget(check { dto ->
            assertEquals(1.0, dto.tasaCambio, 0.001)
            assertEquals(100.0, dto.importeBase, 0.01)
            assertEquals(22.0, dto.iva, 0.01)
            assertEquals(122.0, dto.importeTotalBase, 0.01)

            // Web parity: Base currency original amounts must be 0
            assertEquals(0.0, dto.importeOriginal, 0.001)
            assertEquals(0.0, dto.ivaOriginal, 0.001)
            assertEquals(0.0, dto.importeTotalOriginal, 0.001)
        })).thenReturn(Result.success(501L))

        viewModel.submitForm()
    }

    @Test
    fun `FOREIGN_CURRENCY_SEMANTICS_MATCH_WEB - foreign currency populates original amounts`() = runTest {
        viewModel.selectedCliente = ClienteDto(10, "Cliente Test")
        viewModel.selectedAlmacen = CatalogoItemDto(2, "Almacén")
        viewModel.selectedMoneda = CatalogoItemDto(2, "Dólares", "USD")
        viewModel.tasaCambio = 40.0
        viewModel.preciosIncluyenIva = true

        val prod = ProductoDto(50, "Producto USD", precio = 100.0, tasaIva = 0.22)
        viewModel.addLineItem(prod, cantidad = 1.0)

        whenever(budgetRepository.createBudget(check { dto ->
            assertEquals(40.0, dto.tasaCambio, 0.001)
            // Base amounts = original * rate (40.0)
            assertEquals(4000.0 * (100.0 / 122.0), dto.importeBase, 1.0)

            // Original amounts = foreign currency values
            assertTrue(dto.importeOriginal > 0)
            assertTrue(dto.importeTotalOriginal > 0)
        })).thenReturn(Result.success(502L))

        viewModel.submitForm()
    }

    @Test
    fun `BASE_PRICE_MODE_NET - calculates tax and subtotal for net price mode`() = runTest {
        viewModel.preciosIncluyenIva = false
        val prod = ProductoDto(50, "Producto Neto", precio = 100.0, tasaIva = 0.22)
        viewModel.addLineItem(prod, cantidad = 1.0)

        assertEquals(100.0, viewModel.calculateSubtotal(), 0.01)
        assertEquals(22.0, viewModel.calculateIva(), 0.01)
        assertEquals(122.0, viewModel.calculateTotal(), 0.01)
    }

    @Test
    fun `BASE_PRICE_MODE_GROSS - calculates tax and subtotal for gross price mode`() = runTest {
        viewModel.preciosIncluyenIva = true
        val prod = ProductoDto(50, "Producto IVA Inc", precio = 122.0, tasaIva = 0.22)
        viewModel.addLineItem(prod, cantidad = 1.0)

        assertEquals(100.0, viewModel.calculateSubtotal(), 0.01)
        assertEquals(22.0, viewModel.calculateIva(), 0.01)
        assertEquals(122.0, viewModel.calculateTotal(), 0.01)
    }
}

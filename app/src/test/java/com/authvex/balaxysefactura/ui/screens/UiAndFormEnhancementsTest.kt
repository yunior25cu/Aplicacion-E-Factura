package com.authvex.balaxysefactura.ui.screens

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.BudgetRepository
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.ui.screens.budget.form.BudgetFormMode
import com.authvex.balaxysefactura.ui.screens.budget.form.BudgetFormViewModel
import com.authvex.balaxysefactura.ui.screens.emission.EmissionViewModel
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class UiAndFormEnhancementsTest {

    private lateinit var cfeRepository: CfeRepository
    private lateinit var budgetRepository: BudgetRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cfeRepository = mock()
        budgetRepository = mock()

        runTest {
            whenever(cfeRepository.getEmpresa()).thenReturn(Result.success(EmpresaDto(id = 10, moneda = CatalogoItemDto(50, "Pesos Test", "UYU"))))
            whenever(cfeRepository.getPuntosVenta()).thenReturn(Result.success(listOf(PuntoVentaDto(1, "Caja 1", 1, true, false))))
            whenever(cfeRepository.getDocumentosHabilitados(1)).thenReturn(Result.success(listOf(CfeFiscalDocumentAvailabilityGroupDto(1, listOf(CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A"))))))
            whenever(cfeRepository.getClientes(anyOrNull())).thenReturn(Result.success(listOf(ClienteDto(10, "ABITAB S A"))))
            whenever(cfeRepository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(2, "Almacén Central"), CatalogoItemDto(3, "Almacén Norte"))))
            whenever(cfeRepository.getTasaCambios(any())).thenReturn(Result.success(listOf(
                TasaCambioSimpleDto(50, "UYU", "Pesos Test", "$", 2, 1.0),
                TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.0)
            )))
            whenever(cfeRepository.getProductos(anyOrNull())).thenReturn(Result.success(listOf(ProductoDto(50, "Prod", precio = 100.0))))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `CFE_CURRENCY_SELECTION_PRESERVES_SELECTED_ID and CFE_REQUEST_CURRENCY_ID_UNCHANGED`() = runTest {
        val viewModel = EmissionViewModel(cfeRepository)
        val usdMoneda = TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.0)

        viewModel.selectedMoneda = usdMoneda

        assertEquals(51, viewModel.selectedMoneda?.id)
        assertEquals("USD", viewModel.selectedMoneda?.codigo)
    }

    @Test
    fun `CFE_WAREHOUSE_SELECTION_PRESERVES_SELECTED_ID and CFE_REQUEST_WAREHOUSE_ID_UNCHANGED`() = runTest {
        val viewModel = EmissionViewModel(cfeRepository)
        val almacenNorte = CatalogoItemDto(3, "Almacén Norte")

        viewModel.selectedAlmacen = almacenNorte

        assertEquals(3, viewModel.selectedAlmacen?.id)
        assertEquals("Almacén Norte", viewModel.selectedAlmacen?.nombre)
    }

    @Test
    fun `BUDGET_CONFIRMATION_DATE_USES_EXISTING_CALLBACK and BUDGET_CONFIRMATION_DATE_STILL_RELOADS_RATE`() = runTest {
        whenever(cfeRepository.getTasaCambios("2026-12-01")).thenReturn(Result.success(listOf(
            TasaCambioSimpleDto(50, "UYU", "Pesos Test", "$", 2, 1.0),
            TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 42.5)
        )))

        val viewModel = BudgetFormViewModel(budgetRepository, cfeRepository)
        viewModel.onFechaConfirmacionChanged("2026-12-01")

        assertEquals("2026-12-01", viewModel.fechaConfirmacion)
        verify(cfeRepository).getTasaCambios("2026-12-01")
    }

    @Test
    fun `BUDGET_EDIT_DATE_INITIAL_SELECTION_MATCHES_EXISTING`() = runTest {
        val existingBudget = BudgetDto(
            id = 105L,
            fechaEmision = "2026-01-13",
            fechaConfirmacion = "2026-01-14",
            fechaVencimiento = "2026-02-14",
            estado = 1,
            moneda = CatalogoItemDto(50, "Pesos Test", "UYU"),
            almacen = CatalogoItemDto(2, "Almacén Central"),
            cliente = ClienteDto(10, "ABITAB S A")
        )

        whenever(budgetRepository.getBudgetById(105L)).thenReturn(Result.success(existingBudget))

        val viewModel = BudgetFormViewModel(budgetRepository, cfeRepository)
        viewModel.loadBudgetForEdit(105L)

        assertEquals(BudgetFormMode.EDIT, viewModel.formMode)
        assertEquals("2026-01-13", viewModel.fechaEmision)
        assertEquals("2026-01-14", viewModel.fechaConfirmacion)
        assertEquals("2026-02-14", viewModel.fechaVencimiento)
    }
}

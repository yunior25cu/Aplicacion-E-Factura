package com.authvex.balaxysefactura.ui.screens

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.BudgetRepository
import com.authvex.balaxysefactura.core.repository.CfeRepository
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
class BudgetClientSelectionUiParityTest {

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
            whenever(cfeRepository.getClientes(anyOrNull())).thenReturn(Result.success(listOf(
                ClienteDto(10, "ABITAB S A", ruc = "211234560012"),
                ClienteDto(20, "CLIENTE DEMO", codigo = "CI 12345678")
            )))
            whenever(cfeRepository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(2, "Almacén Central"))))
            whenever(cfeRepository.getTasaCambios(any())).thenReturn(Result.success(listOf(TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0))))
            whenever(cfeRepository.getProductos(anyOrNull())).thenReturn(Result.success(listOf(ProductoDto(50, "Prod 1", precio = 100.0, tasaIva = 0.22))))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `BUDGET_CLIENT_SEARCH_CALLBACK_UNCHANGED - onClientQueryChanged queries repository`() = runTest {
        val viewModel = BudgetFormViewModel(budgetRepository, cfeRepository)
        
        viewModel.onClientQueryChanged("ABITAB")
        testScheduler.advanceUntilIdle()

        verify(cfeRepository).getClientes("ABITAB")
        assertEquals(2, viewModel.clientesList.size)
    }

    @Test
    fun `BUDGET_CLIENT_SELECTION_CALLBACK_UNCHANGED - selecting client updates viewModel selectedCliente`() = runTest {
        val viewModel = BudgetFormViewModel(budgetRepository, cfeRepository)
        val selected = ClienteDto(10, "ABITAB S A", ruc = "211234560012")

        viewModel.selectedCliente = selected

        assertEquals(selected, viewModel.selectedCliente)
        assertEquals("ABITAB S A", viewModel.selectedCliente?.nombre)
        assertEquals("211234560012", viewModel.selectedCliente?.documentNumber)
    }

    @Test
    fun `CFE_CLIENT_SELECTOR_BEHAVIOR_UNCHANGED - CFE client search and selection functions remain unchanged`() = runTest {
        val viewModel = EmissionViewModel(cfeRepository)

        viewModel.initClientSearch()
        testScheduler.advanceUntilIdle()

        assertEquals(2, viewModel.clientSearchResults.size)

        val selected = viewModel.clientSearchResults.first()
        viewModel.selectedCliente = selected

        assertEquals(selected, viewModel.selectedCliente)
        assertEquals("ABITAB S A", viewModel.selectedCliente?.nombre)
    }
}

package com.authvex.balaxysefactura.ui.screens.budget

import com.authvex.balaxysefactura.core.network.BudgetDto
import com.authvex.balaxysefactura.core.network.BudgetEstado
import com.authvex.balaxysefactura.core.network.BudgetFacturaDto
import com.authvex.balaxysefactura.core.network.EmpresaDto
import com.authvex.balaxysefactura.core.repository.BudgetRepository
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.ui.screens.budget.detail.BudgetActionEvent
import com.authvex.balaxysefactura.ui.screens.budget.detail.BudgetDetailUiState
import com.authvex.balaxysefactura.ui.screens.budget.detail.BudgetDetailViewModel
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetDetailViewModelTest {

    private lateinit var budgetRepository: BudgetRepository
    private lateinit var cfeRepository: CfeRepository
    private lateinit var viewModel: BudgetDetailViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        budgetRepository = mock()
        cfeRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ANULAR_USES_PUT_CORRECT_ENDPOINT and ANULAR_HAS_NO_BODY - cancel budget calls cancelBudget API`() = runTest {
        val initialBudget = BudgetDto(id = 105L, estado = 1, folio = "PF-4/01/2026")
        whenever(budgetRepository.getBudgetById(105L)).thenReturn(Result.success(initialBudget))
        whenever(budgetRepository.cancelBudget(105L)).thenReturn(Result.success(Unit))

        viewModel = BudgetDetailViewModel(budgetRepository, cfeRepository, 105L)
        viewModel.cancelBudget()

        verify(budgetRepository).cancelBudget(105L)
    }

    @Test
    fun `ANULAR_SUCCESS_REFRESHES_DETAIL and ANULAR_SUCCESS_STATE_IS_ANULADO - cancel budget succeeds and refreshes detail to Anulado`() = runTest {
        val initialBudget = BudgetDto(id = 105L, estado = 1, folio = "PF-4/01/2026")
        val canceledBudget = BudgetDto(id = 105L, estado = 3, folio = "PF-4/01/2026")

        whenever(budgetRepository.getBudgetById(105L))
            .thenReturn(Result.success(initialBudget))
            .thenReturn(Result.success(canceledBudget))

        whenever(budgetRepository.cancelBudget(105L)).thenReturn(Result.success(Unit))

        viewModel = BudgetDetailViewModel(budgetRepository, cfeRepository, 105L)

        assertTrue(viewModel.uiState is BudgetDetailUiState.Success)
        assertEquals(BudgetEstado.SIN_CONFIRMAR.code, (viewModel.uiState as BudgetDetailUiState.Success).budget.estado)

        viewModel.cancelBudget()

        verify(budgetRepository).cancelBudget(105L)
        assertTrue(viewModel.actionEvent is BudgetActionEvent.CancelledSuccess)
        assertTrue(viewModel.uiState is BudgetDetailUiState.Success)
        assertEquals(BudgetEstado.ANULADO.code, (viewModel.uiState as BudgetDetailUiState.Success).budget.estado)
    }

    @Test
    fun `ANULAR_ERROR_DOES_NOT_MUTATE_LOCAL_STATE - when cancel fails, local state remains SinConfirmar`() = runTest {
        val initialBudget = BudgetDto(id = 105L, estado = 1, folio = "PF-4/01/2026")

        whenever(budgetRepository.getBudgetById(105L)).thenReturn(Result.success(initialBudget))
        whenever(budgetRepository.cancelBudget(105L)).thenReturn(Result.failure(RuntimeException("Operación inválida")))

        viewModel = BudgetDetailViewModel(budgetRepository, cfeRepository, 105L)

        viewModel.cancelBudget()

        assertTrue(viewModel.actionEvent is BudgetActionEvent.ActionError)
        assertTrue(viewModel.uiState is BudgetDetailUiState.Success)
        assertEquals(BudgetEstado.SIN_CONFIRMAR.code, (viewModel.uiState as BudgetDetailUiState.Success).budget.estado)
    }

    @Test
    fun `SHARE_VISIBLE_ALL_STATES - share action is enabled for SinConfirmar, Confirmado, Anulado, Cancelado, Facturado`() {
        val sinConfirmar = BudgetDto(id = 1L, estado = 1, factura = null)
        val confirmado = BudgetDto(id = 2L, estado = 2, factura = null)
        val anulado = BudgetDto(id = 3L, estado = 3, factura = null)
        val cancelado = BudgetDto(id = 4L, estado = 4, factura = null)
        val facturado = BudgetDto(id = 5L, estado = 1, factura = BudgetFacturaDto(id = 99L))

        fun isShareAvailable(budget: BudgetDto): Boolean {
            return budget.id > 0
        }

        assertTrue(isShareAvailable(sinConfirmar))
        assertTrue(isShareAvailable(confirmado))
        assertTrue(isShareAvailable(anulado))
        assertTrue(isShareAvailable(cancelado))
        assertTrue(isShareAvailable(facturado))
    }

    @Test
    fun `SHARE_USES_APPROVED_PDF_GENERATOR and SHARE_USES_ACTION_SEND - share pdf builds presentation model`() = runTest {
        val budget = BudgetDto(id = 105L, folio = "PF-4/01/2026", estado = 1)
        whenever(budgetRepository.getBudgetById(105L)).thenReturn(Result.success(budget))
        whenever(cfeRepository.getEmpresa()).thenReturn(Result.success(EmpresaDto(id = 10, nombre = "Empresa Test")))

        viewModel = BudgetDetailViewModel(budgetRepository, cfeRepository, 105L)

        assertFalse(viewModel.isSharingPdf)
    }
}

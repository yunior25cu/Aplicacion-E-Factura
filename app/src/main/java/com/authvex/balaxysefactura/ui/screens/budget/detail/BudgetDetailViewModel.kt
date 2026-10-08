package com.authvex.balaxysefactura.ui.screens.budget.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.BudgetDto
import com.authvex.balaxysefactura.core.network.ErrorMapper
import com.authvex.balaxysefactura.core.repository.BudgetRepository
import kotlinx.coroutines.launch

sealed class BudgetDetailUiState {
    object Loading : BudgetDetailUiState()
    data class Success(val budget: BudgetDto) : BudgetDetailUiState()
    data class Error(val message: String) : BudgetDetailUiState()
}

sealed class BudgetActionEvent {
    object Idle : BudgetActionEvent()
    object Processing : BudgetActionEvent()
    data class ConfirmedSuccess(val message: String) : BudgetActionEvent()
    data class InvoicedSuccess(val facturaId: Long, val message: String) : BudgetActionEvent()
    data class ActionError(val message: String) : BudgetActionEvent()
}

class BudgetDetailViewModel(
    private val budgetRepository: BudgetRepository,
    val budgetId: Long
) : ViewModel() {

    var uiState by mutableStateOf<BudgetDetailUiState>(BudgetDetailUiState.Loading)
        private set

    var actionEvent by mutableStateOf<BudgetActionEvent>(BudgetActionEvent.Idle)
        private set

    init {
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            uiState = BudgetDetailUiState.Loading
            val result = budgetRepository.getBudgetById(budgetId)
            result.onSuccess { budget ->
                uiState = BudgetDetailUiState.Success(budget)
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                uiState = BudgetDetailUiState.Error(appError.getDisplayMessage())
            }
        }
    }

    fun confirmBudget() {
        if (actionEvent is BudgetActionEvent.Processing) return
        viewModelScope.launch {
            actionEvent = BudgetActionEvent.Processing
            val result = budgetRepository.confirmBudget(budgetId)
            result.onSuccess {
                actionEvent = BudgetActionEvent.ConfirmedSuccess("Presupuesto confirmado correctamente")
                loadDetail()
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                actionEvent = BudgetActionEvent.ActionError(appError.getDisplayMessage())
            }
        }
    }

    fun invoiceBudget() {
        if (actionEvent is BudgetActionEvent.Processing) return
        viewModelScope.launch {
            actionEvent = BudgetActionEvent.Processing
            val result = budgetRepository.invoiceBudget(budgetId)
            result.onSuccess { facturaId ->
                actionEvent = BudgetActionEvent.InvoicedSuccess(
                    facturaId = facturaId,
                    message = "Factura N° $facturaId generada correctamente desde el Presupuesto"
                )
                loadDetail()
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                actionEvent = BudgetActionEvent.ActionError(appError.getDisplayMessage())
            }
        }
    }

    fun resetActionEvent() {
        actionEvent = BudgetActionEvent.Idle
    }
}

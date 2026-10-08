package com.authvex.balaxysefactura.ui.screens.budget.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.BudgetDto
import com.authvex.balaxysefactura.core.network.ErrorMapper
import com.authvex.balaxysefactura.core.repository.BudgetRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class BudgetListUiState {
    object Loading : BudgetListUiState()
    data class Success(
        val budgets: List<BudgetDto>,
        val totalRecords: Int,
        val hasMore: Boolean
    ) : BudgetListUiState()
    data class Error(val message: String) : BudgetListUiState()
}

class BudgetListViewModel(
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    var uiState by mutableStateOf<BudgetListUiState>(BudgetListUiState.Loading)
        private set

    var searchQuery by mutableStateOf("")
        private set

    var selectedEstadoFilter by mutableStateOf<Int?>(null)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    private var currentPage = 1
    private val pageSize = 20
    private var allLoadedBudgets = mutableListOf<BudgetDto>()
    private var searchJob: Job? = null

    init {
        loadInitialBudgets()
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadInitialBudgets()
        }
    }

    fun onEstadoFilterChanged(estado: Int?) {
        if (selectedEstadoFilter == estado) return
        selectedEstadoFilter = estado
        loadInitialBudgets()
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing = true
            loadInitialBudgetsInternal()
            isRefreshing = false
        }
    }

    fun loadMore() {
        val currentState = uiState
        if (currentState is BudgetListUiState.Success && currentState.hasMore && !isRefreshing) {
            viewModelScope.launch {
                currentPage++
                val result = budgetRepository.getBudgets(
                    pagina = currentPage,
                    registrosPorPagina = pageSize,
                    busqueda = searchQuery.takeIf { it.isNotBlank() },
                    estado = selectedEstadoFilter
                )
                result.onSuccess { response ->
                    allLoadedBudgets.addAll(response.items)
                    val hasMore = allLoadedBudgets.size < response.totalRecords
                    uiState = BudgetListUiState.Success(
                        budgets = allLoadedBudgets.toList(),
                        totalRecords = response.totalRecords,
                        hasMore = hasMore
                    )
                }.onFailure {
                    // Mantiene la lista previa ante error de paginación
                }
            }
        }
    }

    private fun loadInitialBudgets() {
        viewModelScope.launch {
            loadInitialBudgetsInternal()
        }
    }

    private suspend fun loadInitialBudgetsInternal() {
        uiState = BudgetListUiState.Loading
        currentPage = 1
        allLoadedBudgets.clear()

        val result = budgetRepository.getBudgets(
            pagina = currentPage,
            registrosPorPagina = pageSize,
            busqueda = searchQuery.takeIf { it.isNotBlank() },
            estado = selectedEstadoFilter
        )

        result.onSuccess { response ->
            allLoadedBudgets.addAll(response.items)
            val hasMore = allLoadedBudgets.size < response.totalRecords
            uiState = BudgetListUiState.Success(
                budgets = allLoadedBudgets.toList(),
                totalRecords = response.totalRecords,
                hasMore = hasMore
            )
        }.onFailure { throwable ->
            val appError = ErrorMapper.fromThrowable(throwable)
            uiState = BudgetListUiState.Error(appError.getDisplayMessage())
        }
    }
}

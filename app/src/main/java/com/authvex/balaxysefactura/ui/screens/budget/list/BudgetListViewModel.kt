package com.authvex.balaxysefactura.ui.screens.budget.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.BudgetDto
import com.authvex.balaxysefactura.core.network.ErrorMapper
import com.authvex.balaxysefactura.core.repository.BudgetRepository
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.ui.screens.cfe.list.NumberSortDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface BudgetCfeReferenceState {
    object Loading : BudgetCfeReferenceState
    data class Resolved(val serie: String, val numero: Long) : BudgetCfeReferenceState
    object Pending : BudgetCfeReferenceState
    object Failed : BudgetCfeReferenceState

    fun getDisplayLabel(): String = when (this) {
        is Loading -> "Factura vinculada"
        is Resolved -> "Factura CFE $serie-$numero"
        is Pending -> "Factura CFE pendiente"
        is Failed -> "CFE no disponible"
    }
}

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
    private val budgetRepository: BudgetRepository,
    private val cfeRepository: CfeRepository? = null
) : ViewModel() {

    var uiState by mutableStateOf<BudgetListUiState>(BudgetListUiState.Loading)
        private set

    var searchQuery by mutableStateOf("")
        private set

    var selectedEstadoFilter by mutableStateOf<Int?>(null)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var cfeReferences by mutableStateOf<Map<Long, BudgetCfeReferenceState>>(emptyMap())
        private set

    var numberSortDirection by mutableStateOf(NumberSortDirection.DESC)
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
            invalidatePendingOrFailedReferences()
            loadInitialBudgetsInternal()
            isRefreshing = false
        }
    }

    fun toggleNumberSort() {
        numberSortDirection = if (numberSortDirection == NumberSortDirection.DESC) {
            NumberSortDirection.ASC
        } else {
            NumberSortDirection.DESC
        }
        loadInitialBudgets()
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
                    estado = selectedEstadoFilter,
                    ordering = "Documento.Numero",
                    sortDirection = numberSortDirection.apiValue
                )
                result.onSuccess { response ->
                    allLoadedBudgets.addAll(response.items)
                    resolveCfeReferences(response.items)
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
            invalidatePendingOrFailedReferences()
            loadInitialBudgetsInternal()
        }
    }

    private fun invalidatePendingOrFailedReferences() {
        cfeReferences = cfeReferences.filterValues { it is BudgetCfeReferenceState.Resolved }
    }

    private suspend fun loadInitialBudgetsInternal() {
        uiState = BudgetListUiState.Loading
        currentPage = 1
        allLoadedBudgets.clear()

        val result = budgetRepository.getBudgets(
            pagina = currentPage,
            registrosPorPagina = pageSize,
            busqueda = searchQuery.takeIf { it.isNotBlank() },
            estado = selectedEstadoFilter,
            ordering = "Documento.Numero",
            sortDirection = numberSortDirection.apiValue
        )

        result.onSuccess { response ->
            allLoadedBudgets.addAll(response.items)
            resolveCfeReferences(response.items)
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

    private fun resolveCfeReferences(budgets: List<BudgetDto>) {
        val repo = cfeRepository ?: return
        budgets.mapNotNull { it.factura }.distinctBy { it.id }.forEach { factura ->
            val facturaId = factura.id
            val currentState = cfeReferences[facturaId]
            if (currentState == null || currentState is BudgetCfeReferenceState.Failed || currentState is BudgetCfeReferenceState.Pending) {
                // Set loading state in map
                if (currentState == null) {
                    cfeReferences = cfeReferences + (facturaId to BudgetCfeReferenceState.Loading)
                }

                viewModelScope.launch {
                    repo.getDocumentDetail(facturaId.toInt()).onSuccess { detail ->
                        val newState = if (!detail.serie.isNullOrBlank() && detail.numero != null && detail.numero > 0) {
                            BudgetCfeReferenceState.Resolved(detail.serie, detail.numero)
                        } else {
                            BudgetCfeReferenceState.Pending
                        }
                        cfeReferences = cfeReferences + (facturaId to newState)
                    }.onFailure {
                        cfeReferences = cfeReferences + (facturaId to BudgetCfeReferenceState.Failed)
                    }
                }
            }
        }
    }
}

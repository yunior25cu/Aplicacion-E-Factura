package com.authvex.balaxysefactura.ui.screens.cfe.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.AppError
import com.authvex.balaxysefactura.core.network.CfeSummaryDto
import com.authvex.balaxysefactura.core.repository.CfeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class CfeListUiState {
    object LoadingInitial : CfeListUiState()
    // Backward compatibility for legacy tests
    @Deprecated("Usar LoadingInitial")
    val Loading = LoadingInitial

    object Empty : CfeListUiState()
    data class Success(
        val documents: List<CfeSummaryDto>,
        val totalRecords: Int,
        val canLoadMore: Boolean = false,
        val isFetchingNextPage: Boolean = false,
        val nextPageError: AppError? = null
    ) : CfeListUiState()
    data class Error(val error: AppError) : CfeListUiState()
}

enum class NumberSortDirection(val apiValue: String) {
    DESC("desc"),
    ASC("asc")
}

class CfeListViewModel(private val repository: CfeRepository) : ViewModel() {

    var uiState by mutableStateOf<CfeListUiState>(CfeListUiState.LoadingInitial)
        private set

    var searchQuery by mutableStateOf("")
        private set

    var numberSortDirection by mutableStateOf(NumberSortDirection.DESC)
        private set

    private var currentPage = 1
    private var totalRecords = 0
    private var accumulatedDocuments = mutableListOf<CfeSummaryDto>()
    private var isFetchingNextPage = false
    private var searchJob: Job? = null

    init {
        loadDocuments()
    }

    fun toggleNumberSort() {
        numberSortDirection = if (numberSortDirection == NumberSortDirection.DESC) {
            NumberSortDirection.ASC
        } else {
            NumberSortDirection.DESC
        }
        loadDocuments(isRefresh = true)
    }

    fun loadDocuments(isRefresh: Boolean = false) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (isRefresh || accumulatedDocuments.isEmpty()) {
                uiState = CfeListUiState.LoadingInitial
            }
            currentPage = 1
            isFetchingNextPage = false
            
            repository.searchDocuments(
                query = searchQuery.ifBlank { null },
                page = 1,
                ordering = "Numero",
                sortDirection = numberSortDirection.apiValue
            ).onSuccess { response ->
                totalRecords = response.totalRecords
                accumulatedDocuments = response.items.toMutableList()
                
                uiState = if (accumulatedDocuments.isEmpty()) {
                    CfeListUiState.Empty
                } else {
                    CfeListUiState.Success(
                        documents = accumulatedDocuments.toList(),
                        totalRecords = totalRecords,
                        canLoadMore = accumulatedDocuments.size < totalRecords,
                        isFetchingNextPage = false,
                        nextPageError = null
                    )
                }
            }.onFailure { error ->
                val appError = (error as? AppError) ?: AppError.Unexpected(error.message ?: "Error desconocido")
                uiState = CfeListUiState.Error(appError)
            }
        }
    }

    fun loadNextPage() {
        if (isFetchingNextPage) return
        val currentState = uiState as? CfeListUiState.Success ?: return
        if (!currentState.canLoadMore) return

        isFetchingNextPage = true
        uiState = currentState.copy(
            isFetchingNextPage = true,
            nextPageError = null
        )

        val nextPage = currentPage + 1
        viewModelScope.launch {
            repository.searchDocuments(
                query = searchQuery.ifBlank { null },
                page = nextPage,
                ordering = "Numero",
                sortDirection = numberSortDirection.apiValue
            ).onSuccess { response ->
                isFetchingNextPage = false
                currentPage = nextPage
                totalRecords = response.totalRecords

                // Evitar duplicados por documentoId
                val newItems = response.items.filter { newItem ->
                    accumulatedDocuments.none { existing -> existing.documentoId == newItem.documentoId }
                }
                accumulatedDocuments.addAll(newItems)

                uiState = CfeListUiState.Success(
                    documents = accumulatedDocuments.toList(),
                    totalRecords = totalRecords,
                    canLoadMore = accumulatedDocuments.size < totalRecords,
                    isFetchingNextPage = false,
                    nextPageError = null
                )
            }.onFailure { error ->
                isFetchingNextPage = false
                val appError = (error as? AppError) ?: AppError.Unexpected(error.message ?: "Error desconocido")
                uiState = currentState.copy(
                    isFetchingNextPage = false,
                    nextPageError = appError
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotEmpty()) {
                delay(300)
            }
            loadDocuments(isRefresh = true)
        }
    }

    fun retryNextPage() {
        loadNextPage()
    }
}

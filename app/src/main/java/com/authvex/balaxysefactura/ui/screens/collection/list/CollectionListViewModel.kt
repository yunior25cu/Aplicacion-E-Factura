package com.authvex.balaxysefactura.ui.screens.collection.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.CobroSummaryDto
import com.authvex.balaxysefactura.core.network.ErrorMapper
import com.authvex.balaxysefactura.core.repository.CollectionRepository
import com.authvex.balaxysefactura.ui.screens.cfe.list.NumberSortDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class CollectionListUiState {
    object Loading : CollectionListUiState()
    data class Success(
        val collections: List<CobroSummaryDto>,
        val totalRecords: Int,
        val hasMore: Boolean
    ) : CollectionListUiState()
    data class Error(val message: String) : CollectionListUiState()
}

class CollectionListViewModel(
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    var uiState by mutableStateOf<CollectionListUiState>(CollectionListUiState.Loading)
        private set

    var searchQuery by mutableStateOf("")
        private set

    var selectedEstadoFilter by mutableStateOf<Int?>(null)
        private set

    var numberSortDirection by mutableStateOf(NumberSortDirection.DESC)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    private var currentPage = 1
    private val pageSize = 20
    private var allLoadedCollections = mutableListOf<CobroSummaryDto>()
    private var searchJob: Job? = null

    init {
        loadInitialCollections()
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadInitialCollections()
        }
    }

    fun onEstadoFilterChanged(estado: Int?) {
        if (selectedEstadoFilter == estado) return
        selectedEstadoFilter = estado
        loadInitialCollections()
    }

    fun toggleNumberSort() {
        numberSortDirection = if (numberSortDirection == NumberSortDirection.DESC) {
            NumberSortDirection.ASC
        } else {
            NumberSortDirection.DESC
        }
        loadInitialCollections()
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing = true
            loadInitialCollectionsInternal()
            isRefreshing = false
        }
    }

    fun loadMore() {
        val currentState = uiState
        if (currentState is CollectionListUiState.Success && currentState.hasMore && !isRefreshing) {
            viewModelScope.launch {
                currentPage++
                val result = collectionRepository.getCobros(
                    pagina = currentPage,
                    registrosPorPagina = pageSize,
                    busqueda = searchQuery.takeIf { it.isNotBlank() },
                    estado = selectedEstadoFilter,
                    ordering = "Documento.Numero",
                    sortDirection = numberSortDirection.apiValue
                )
                result.onSuccess { response ->
                    allLoadedCollections.addAll(response.items)
                    val hasMore = allLoadedCollections.size < response.totalRecords
                    uiState = CollectionListUiState.Success(
                        collections = allLoadedCollections.toList(),
                        totalRecords = response.totalRecords,
                        hasMore = hasMore
                    )
                }.onFailure {
                    // Mantiene la lista previa ante error de paginación
                }
            }
        }
    }

    private fun loadInitialCollections() {
        viewModelScope.launch {
            loadInitialCollectionsInternal()
        }
    }

    private suspend fun loadInitialCollectionsInternal() {
        uiState = CollectionListUiState.Loading
        currentPage = 1
        allLoadedCollections.clear()

        val result = collectionRepository.getCobros(
            pagina = currentPage,
            registrosPorPagina = pageSize,
            busqueda = searchQuery.takeIf { it.isNotBlank() },
            estado = selectedEstadoFilter,
            ordering = "Documento.Numero",
            sortDirection = numberSortDirection.apiValue
        )

        result.onSuccess { response ->
            allLoadedCollections.addAll(response.items)
            val hasMore = allLoadedCollections.size < response.totalRecords
            uiState = CollectionListUiState.Success(
                collections = allLoadedCollections.toList(),
                totalRecords = response.totalRecords,
                hasMore = hasMore
            )
        }.onFailure { throwable ->
            val appError = ErrorMapper.fromThrowable(throwable)
            uiState = CollectionListUiState.Error(appError.getDisplayMessage())
        }
    }
}

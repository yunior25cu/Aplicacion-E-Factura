package com.authvex.balaxysefactura.ui.screens.cfe.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.AppError
import com.authvex.balaxysefactura.core.network.CfeSummaryDto
import com.authvex.balaxysefactura.core.repository.CfeRepository
import kotlinx.coroutines.launch

sealed class CfeListUiState {
    object Loading : CfeListUiState()
    object Empty : CfeListUiState()
    data class Success(
        val documents: List<CfeSummaryDto>,
        val totalRecords: Int
    ) : CfeListUiState()
    data class Error(val error: AppError) : CfeListUiState()
}

class CfeListViewModel(private val repository: CfeRepository) : ViewModel() {

    var uiState by mutableStateOf<CfeListUiState>(CfeListUiState.Loading)
        private set

    init {
        loadDocuments()
    }

    fun loadDocuments() {
        viewModelScope.launch {
            uiState = CfeListUiState.Loading
            repository.searchDocuments().onSuccess { response ->
                uiState = if (response.items.isEmpty()) {
                    CfeListUiState.Empty
                } else {
                    CfeListUiState.Success(
                        documents = response.items,
                        totalRecords = response.totalRecords
                    )
                }
            }.onFailure { error ->
                uiState = CfeListUiState.Error(error as? AppError ?: AppError.Unexpected(error.message ?: "Error desconocido"))
            }
        }
    }
}

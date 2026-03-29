package com.authvex.balaxysefactura.ui.screens.cfe.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.AppError
import com.authvex.balaxysefactura.core.network.CfeDetailDto
import com.authvex.balaxysefactura.core.repository.CfeRepository
import kotlinx.coroutines.launch

sealed class CfeDetailUiState {
    object Loading : CfeDetailUiState()
    data class Success(val document: CfeDetailDto) : CfeDetailUiState()
    data class Error(val error: AppError) : CfeDetailUiState()
}

class CfeDetailViewModel(
    private val repository: CfeRepository,
    private val documentoId: Int
) : ViewModel() {

    var uiState by mutableStateOf<CfeDetailUiState>(CfeDetailUiState.Loading)
        private set

    init {
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            uiState = CfeDetailUiState.Loading
            repository.getDocumentDetail(documentoId).onSuccess { doc ->
                uiState = CfeDetailUiState.Success(doc)
            }.onFailure { error ->
                uiState = CfeDetailUiState.Error(error as? AppError ?: AppError.Unexpected(error.message ?: "Error desconocido"))
            }
        }
    }
}

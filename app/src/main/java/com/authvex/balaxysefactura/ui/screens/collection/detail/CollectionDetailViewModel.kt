package com.authvex.balaxysefactura.ui.screens.collection.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.CobroDetailDto
import com.authvex.balaxysefactura.core.network.ErrorMapper
import com.authvex.balaxysefactura.core.repository.CollectionRepository
import kotlinx.coroutines.launch

sealed class CollectionDetailUiState {
    object Loading : CollectionDetailUiState()
    data class Success(val collection: CobroDetailDto) : CollectionDetailUiState()
    data class Error(val message: String) : CollectionDetailUiState()
}

class CollectionDetailViewModel(
    private val collectionRepository: CollectionRepository,
    val collectionId: Long
) : ViewModel() {

    var uiState by mutableStateOf<CollectionDetailUiState>(CollectionDetailUiState.Loading)
        private set

    var isConfirming by mutableStateOf(false)
        private set

    var confirmMessage by mutableStateOf<String?>(null)

    init {
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            uiState = CollectionDetailUiState.Loading
            val result = collectionRepository.getCobroById(collectionId)
            result.onSuccess { collection ->
                uiState = CollectionDetailUiState.Success(collection)
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                uiState = CollectionDetailUiState.Error(appError.getDisplayMessage())
            }
        }
    }

    fun confirmCollection() {
        if (isConfirming) return
        isConfirming = true
        confirmMessage = null

        viewModelScope.launch {
            try {
                val result = collectionRepository.confirmCobro(collectionId)
                result.onSuccess {
                    confirmMessage = "Cobro confirmado correctamente"
                    loadDetail()
                }.onFailure { throwable ->
                    val appError = ErrorMapper.fromThrowable(throwable)
                    confirmMessage = appError.getDisplayMessage()
                }
            } finally {
                isConfirming = false
            }
        }
    }
}

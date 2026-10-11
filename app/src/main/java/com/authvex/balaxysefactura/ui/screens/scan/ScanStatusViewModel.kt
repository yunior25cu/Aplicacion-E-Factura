package com.authvex.balaxysefactura.ui.screens.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.ErrorMapper
import com.authvex.balaxysefactura.core.network.ScanStatusDto
import com.authvex.balaxysefactura.core.repository.ScanRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface ScanStatusUiState {
    object Loading : ScanStatusUiState
    data class Success(val status: ScanStatusDto, val isTimeout: Boolean = false) : ScanStatusUiState
    data class Error(val message: String) : ScanStatusUiState
}

class ScanStatusViewModel(
    private val scanRepository: ScanRepository,
    val documentId: Long
) : ViewModel() {

    var uiState: ScanStatusUiState by mutableStateOf(ScanStatusUiState.Loading)
        private set

    private var pollJob: Job? = null

    init {
        startPolling()
    }

    fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            uiState = ScanStatusUiState.Loading

            var attempts = 0
            val maxAttempts = 60 // 60 attempts * 3s = 180s (~3 min limit)

            while (attempts < maxAttempts) {
                attempts++
                val result = scanRepository.getStatus(documentId)

                if (result.isSuccess) {
                    val status = result.getOrNull()!!
                    val isDone = status.ingestionStatus == 5 || status.ingestionStatus == 6 || status.queueStatus == 4

                    uiState = ScanStatusUiState.Success(status, isTimeout = false)

                    if (isDone) {
                        break
                    }
                } else if (attempts == 1) {
                    val throwable = result.exceptionOrNull()!!
                    val appError = ErrorMapper.fromThrowable(throwable)
                    uiState = ScanStatusUiState.Error(appError.getDisplayMessage())
                    break
                }

                delay(3000)
            }

            // If timeout reached without explicit completion or error
            val currentState = uiState
            if (attempts >= maxAttempts && currentState is ScanStatusUiState.Success && !isStatusTerminal(currentState.status)) {
                uiState = ScanStatusUiState.Success(currentState.status, isTimeout = true)
            }
        }
    }

    private fun isStatusTerminal(status: ScanStatusDto): Boolean {
        return status.ingestionStatus == 5 || status.ingestionStatus == 6 || status.queueStatus == 4
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}

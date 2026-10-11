package com.authvex.balaxysefactura.ui.screens.scan.inbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.ErrorMapper
import com.authvex.balaxysefactura.core.network.ScanInboxItemDto
import com.authvex.balaxysefactura.core.network.ScanTarget
import com.authvex.balaxysefactura.core.repository.ScanRepository
import kotlinx.coroutines.launch

enum class ScanStatusFilter(val label: String) {
    ALL("Todos"),
    PROCESSING("Procesando"),
    READY("Listos"),
    ERROR("Error")
}

sealed interface ScanInboxUiState {
    object Loading : ScanInboxUiState
    data class Success(val items: List<ScanInboxItemDto>) : ScanInboxUiState
    data class Error(val message: String) : ScanInboxUiState
}

class ScanInboxViewModel(
    private val scanRepository: ScanRepository
) : ViewModel() {

    var uiState: ScanInboxUiState by mutableStateOf(ScanInboxUiState.Loading)
        private set

    var selectedTargetFilter by mutableStateOf<ScanTarget?>(null) // null = Todos
    var selectedStatusFilter by mutableStateOf(ScanStatusFilter.ALL)

    private var rawInboxItems = listOf<ScanInboxItemDto>()

    init {
        loadInbox()
    }

    fun loadInbox() {
        viewModelScope.launch {
            uiState = ScanInboxUiState.Loading
            val result = scanRepository.getInbox(selectedTargetFilter)

            result.onSuccess { items ->
                rawInboxItems = items
                applyFiltersAndEmit()
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                uiState = ScanInboxUiState.Error(appError.getDisplayMessage())
            }
        }
    }

    fun onTargetFilterSelected(target: ScanTarget?) {
        if (selectedTargetFilter != target) {
            selectedTargetFilter = target
            loadInbox()
        }
    }

    fun onStatusFilterSelected(statusFilter: ScanStatusFilter) {
        selectedStatusFilter = statusFilter
        applyFiltersAndEmit()
    }

    private fun applyFiltersAndEmit() {
        val filtered = rawInboxItems.filter { item ->
            when (selectedStatusFilter) {
                ScanStatusFilter.ALL -> true
                ScanStatusFilter.PROCESSING -> item.ingestionStatus in 1..4
                ScanStatusFilter.READY -> item.ingestionStatus == 5
                ScanStatusFilter.ERROR -> item.ingestionStatus == 6
            }
        }
        uiState = ScanInboxUiState.Success(filtered)
    }

    fun getEmptyStateMessage(): String {
        return when {
            selectedStatusFilter == ScanStatusFilter.PROCESSING -> "No hay documentos procesándose."
            selectedStatusFilter == ScanStatusFilter.READY -> "No hay documentos listos para revisión."
            selectedStatusFilter == ScanStatusFilter.ERROR -> "No hay documentos con error."
            selectedTargetFilter == ScanTarget.SALE -> "No hay ventas escaneadas."
            selectedTargetFilter == ScanTarget.PURCHASE -> "No hay compras escaneadas."
            else -> "No hay documentos escaneados."
        }
    }
}

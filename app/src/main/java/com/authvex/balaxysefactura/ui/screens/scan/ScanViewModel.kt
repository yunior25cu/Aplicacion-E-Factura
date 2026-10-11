package com.authvex.balaxysefactura.ui.screens.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.ErrorMapper
import com.authvex.balaxysefactura.core.network.ScanTarget
import com.authvex.balaxysefactura.core.repository.ScanRepository
import kotlinx.coroutines.launch
import java.io.File

sealed interface ScanUiState {
    object Idle : ScanUiState
    object Loading : ScanUiState
    data class Success(val documentId: Long) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

class ScanViewModel(
    private val scanRepository: ScanRepository
) : ViewModel() {

    var uiState: ScanUiState by mutableStateOf(ScanUiState.Idle)
        private set

    var scannedFile: File? by mutableStateOf(null)
        private set

    var selectedTarget by mutableStateOf(ScanTarget.SALE)

    fun onDocumentScanned(file: File) {
        scannedFile = file
    }

    fun uploadDocument() {
        val file = scannedFile ?: return
        val target = selectedTarget

        val maxBytes = 25L * 1024 * 1024
        if (file.length() > maxBytes) {
            uiState = ScanUiState.Error("El archivo excede el límite permitido de 25 MB")
            return
        }

        val contentType = getMimeType(file)
        val allowedTypes = listOf("application/pdf", "image/jpeg", "image/png", "image/tiff")

        if (contentType == null || contentType !in allowedTypes) {
            uiState = ScanUiState.Error("Formato no soportado. Permitidos: PDF, JPEG, PNG, TIFF")
            return
        }

        viewModelScope.launch {
            uiState = ScanUiState.Loading
            val result = scanRepository.uploadScannedDocument(file, contentType, target)
            result.onSuccess { docId ->
                uiState = ScanUiState.Success(docId)
                try { file.delete() } catch (_: Exception) {}
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                uiState = ScanUiState.Error(appError.getDisplayMessage())
            }
        }
    }

    fun getMimeType(file: File): String? {
        return when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "tif", "tiff" -> "image/tiff"
            else -> null
        }
    }

    fun reset() {
        uiState = ScanUiState.Idle
        scannedFile = null
    }
}

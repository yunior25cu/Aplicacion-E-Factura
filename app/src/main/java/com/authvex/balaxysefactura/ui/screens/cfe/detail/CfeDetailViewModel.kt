package com.authvex.balaxysefactura.ui.screens.cfe.detail

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.AppError
import com.authvex.balaxysefactura.core.network.CfeDetailDto
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.ui.screens.common.PdfShareHelper
import kotlinx.coroutines.launch

sealed class CfeDetailUiState {
    object Loading : CfeDetailUiState()
    data class Success(val document: CfeDetailDto) : CfeDetailUiState()
    data class Error(val error: AppError) : CfeDetailUiState()
}

class CfeDetailViewModel(
    private val repository: CfeRepository,
    private val documentoId: Long
) : ViewModel() {

    var uiState by mutableStateOf<CfeDetailUiState>(CfeDetailUiState.Loading)
        private set

    var isSharingPdf by mutableStateOf(false)
        private set

    var shareError by mutableStateOf<String?>(null)

    init {
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            uiState = CfeDetailUiState.Loading
            repository.getDocumentDetail(documentoId.toInt()).onSuccess { doc ->
                uiState = CfeDetailUiState.Success(doc)
            }.onFailure { error ->
                uiState = CfeDetailUiState.Error(error as? AppError ?: AppError.Unexpected(error.message ?: "Error desconocido"))
            }
        }
    }

    fun shareCfePdf(context: Context) {
        if (isSharingPdf) return
        val currentDoc = (uiState as? CfeDetailUiState.Success)?.document ?: return

        isSharingPdf = true
        shareError = null

        viewModelScope.launch {
            val downloadResult = CfePdfDownloader.downloadAndSave(
                context = context,
                cfeRepository = repository,
                documentId = documentoId,
                cfeDetail = currentDoc
            )

            downloadResult.onSuccess { pdfFile ->
                val subject = if (currentDoc.cfeCode != null && !currentDoc.serie.isNullOrBlank() && currentDoc.numero != null) {
                    "CFE ${currentDoc.cfeCode} ${currentDoc.serie}-${currentDoc.numero}"
                } else {
                    "Comprobante Electrónico N° $documentoId"
                }

                val shareResult = PdfShareHelper.sharePdf(
                    context = context,
                    pdfFile = pdfFile,
                    subject = subject,
                    text = "Adjunto $subject",
                    chooserTitle = "Compartir comprobante electrónico"
                )

                if (shareResult.isFailure) {
                    shareError = "Error al abrir opciones de compartir: ${shareResult.exceptionOrNull()?.message}"
                }
            }.onFailure { throwable ->
                shareError = throwable.message ?: "La representación fiscal todavía no está disponible para este CFE."
            }

            isSharingPdf = false
        }
    }
}

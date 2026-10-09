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

    var formattedTotalText by mutableStateOf<String?>(null)
        private set

    init {
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            uiState = CfeDetailUiState.Loading
            val detailResult = repository.getDocumentDetail(documentoId.toInt())
            if (detailResult.isFailure) {
                uiState = CfeDetailUiState.Error(
                    (detailResult.exceptionOrNull() as? AppError)
                        ?: AppError.Unexpected(detailResult.exceptionOrNull()?.message ?: "Error al cargar detalle")
                )
                return@launch
            }

            val doc = detailResult.getOrThrow()

            // Fetch base currency id from Empresa
            val empresaRes = repository.getEmpresa().getOrNull()
            val baseCurrencyId = empresaRes?.moneda?.id ?: 50

            // Fetch ERP document (Factura or Devolucion)
            val erpDocRes = if (isDevolucionCode(doc.cfeCode)) {
                repository.getDevolucionById(documentoId)
            } else {
                repository.getFacturaById(documentoId)
            }

            val erpDoc = erpDocRes.getOrNull()
            if (erpDocRes.isSuccess && erpDoc != null) {
                val isBaseCurrency = (erpDoc.moneda?.id == baseCurrencyId)
                if (isBaseCurrency) {
                    val amount = erpDoc.importeTotalBase ?: doc.importeTotal ?: 0.0
                    val symbol = doc.monedaSimbolo ?: "$"
                    formattedTotalText = "$symbol ${String.format(java.util.Locale.US, "%.2f", amount)}"
                } else {
                    val amount = erpDoc.importeTotalOriginal ?: 0.0
                    val code = erpDoc.moneda?.codigo ?: doc.monedaCodigo ?: "USD"
                    formattedTotalText = "$code ${String.format(java.util.Locale.US, "%.2f", amount)}"
                }
            } else {
                val amount = doc.importeTotal ?: 0.0
                val symbol = doc.monedaSimbolo ?: "$"
                formattedTotalText = "$symbol ${String.format(java.util.Locale.US, "%.2f", amount)}"
            }

            uiState = CfeDetailUiState.Success(doc)
        }
    }

    private fun isDevolucionCode(code: Int?): Boolean {
        return code in listOf(102, 103, 112, 113, 122, 123, 132, 133, 142, 143, 152, 153)
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

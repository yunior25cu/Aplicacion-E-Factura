package com.authvex.balaxysefactura.ui.screens.collection.detail

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.AppError
import com.authvex.balaxysefactura.core.network.CobroDetailDto
import com.authvex.balaxysefactura.core.network.CollectionInvoiceDto
import com.authvex.balaxysefactura.core.network.ErrorMapper
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.core.repository.CollectionRepository
import com.authvex.balaxysefactura.ui.screens.common.PdfShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class CollectionDetailUiState {
    object Loading : CollectionDetailUiState()
    data class Success(val collection: CobroDetailDto) : CollectionDetailUiState()
    data class Error(val message: String) : CollectionDetailUiState()
}

class CollectionDetailViewModel(
    private val repository: CfeRepository,
    private val documentoId: Long,
    private val collectionRepository: CollectionRepository? = null
) : ViewModel() {

    var uiState by mutableStateOf<CollectionDetailUiState>(CollectionDetailUiState.Loading)
        private set

    var isConfirming by mutableStateOf(false)
        private set

    var confirmMessage by mutableStateOf<String?>(null)

    var isGeneratingReceipt by mutableStateOf(false)
        private set

    var receiptError by mutableStateOf<String?>(null)

    var linkedFactura by mutableStateOf<CollectionInvoiceDto?>(null)
        private set

    var baseCurrencyId by mutableStateOf<Int?>(null)
        private set

    init {
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            uiState = CollectionDetailUiState.Loading

            if (collectionRepository != null) {
                val cobroRes = collectionRepository.getCobroById(documentoId)
                if (cobroRes != null && cobroRes.isSuccess) {
                    val collection = cobroRes.getOrNull()
                    if (collection != null) {
                        uiState = CollectionDetailUiState.Success(collection)
                        val empresaRes = repository.getEmpresa().getOrNull()
                        baseCurrencyId = empresaRes?.moneda?.id ?: 50
                        return@launch
                    }
                }
            }

            val detailResult = repository.getDocumentDetail(documentoId.toInt())
            if (detailResult.isFailure) {
                val appErr = (detailResult.exceptionOrNull() as? AppError)
                    ?: AppError.Unexpected(detailResult.exceptionOrNull()?.message ?: "Error al cargar detalle")
                uiState = CollectionDetailUiState.Error(appErr.getDisplayMessage())
                return@launch
            }

            val doc = detailResult.getOrThrow()
            val empresaRes = repository.getEmpresa().getOrNull()
            baseCurrencyId = empresaRes?.moneda?.id ?: 50
        }
    }

    fun confirmCollection() {
        if (isConfirming) return
        val collRepo = collectionRepository ?: return
        isConfirming = true
        confirmMessage = null

        viewModelScope.launch {
            try {
                val result = collRepo.confirmCobro(documentoId)
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

    private fun formatDateDisplay(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        val clean = dateStr.take(10)
        val parts = clean.split("-")
        return if (parts.size == 3) {
            "${parts[2]}/${parts[1]}/${parts[0]}"
        } else {
            clean
        }
    }

    suspend fun resolveAppliedInvoicesForReceipt(
        collection: CobroDetailDto,
        cfeRepository: CfeRepository?
    ): List<ReceiptAppliedInvoiceModel> = withContext(Dispatchers.IO) {
        val uniqueDocIds = collection.facturaCobros.map { it.factura.id }.filter { it > 0 }.distinct()
        val cfeMap = mutableMapOf<Long, String>()

        if (cfeRepository != null) {
            uniqueDocIds.forEach { docId ->
                try {
                    val cfeRes = cfeRepository.getDocumentDetail(docId.toInt())
                    if (cfeRes.isSuccess) {
                        val detail = cfeRes.getOrNull()
                        if (detail != null && !detail.serie.isNullOrBlank() && detail.numero != null && detail.numero > 0) {
                            cfeMap[docId] = "${detail.serie}-${detail.numero}"
                        }
                    }
                } catch (_: Exception) {
                    // Fallback to internal folio
                }
            }
        }

        collection.facturaCobros.map { fc ->
            val docId = fc.factura.id
            val internalFolio = fc.factura.folio?.takeIf { it.isNotBlank() } ?: "Factura N° $docId"
            val fiscalRef = cfeMap[docId] ?: internalFolio
            val isFallback = !cfeMap.containsKey(docId)

            val rawDate = fc.factura.fechaConfirmacion.takeIf { it.isNotBlank() } ?: fc.factura.fechaEmision
            val formattedDate = formatDateDisplay(rawDate)
            val amount = if (fc.montoOriginal > 0) fc.montoOriginal else fc.montoBase

            ReceiptAppliedInvoiceModel(
                documentId = docId,
                fiscalReference = fiscalRef,
                isFallback = isFallback,
                fecha = formattedDate,
                amount = amount
            )
        }
    }

    fun generateReceiptAndExecute(
        context: Context,
        cfeRepository: CfeRepository? = null,
        action: (File) -> Result<Unit>
    ) {
        if (isGeneratingReceipt) return
        val currentSuccessState = uiState as? CollectionDetailUiState.Success ?: return
        if (currentSuccessState.collection.estado != 2) return // Only for Confirmado (estado == 2)

        val collRepo = collectionRepository ?: return

        isGeneratingReceipt = true
        receiptError = null

        viewModelScope.launch {
            try {
                val refetchRes = collRepo.getCobroById(documentoId)
                if (refetchRes.isFailure) {
                    val appError = ErrorMapper.fromThrowable(refetchRes.exceptionOrNull()!!)
                    receiptError = appError.getDisplayMessage()
                    return@launch
                }

                val collection = refetchRes.getOrNull()!!
                uiState = CollectionDetailUiState.Success(collection)

                val repoForCfe = cfeRepository ?: repository
                val company = repoForCfe.getEmpresa().getOrNull()
                val appliedInvoices = resolveAppliedInvoicesForReceipt(collection, repoForCfe)

                val pdfResult = CollectionReceiptPdfGenerator.generate(context, collection, company, appliedInvoices)

                pdfResult.onSuccess { pdfFile ->
                    val actionResult = action(pdfFile)
                    if (actionResult.isFailure) {
                        receiptError = actionResult.exceptionOrNull()?.message ?: "Error al procesar el recibo"
                    }
                }.onFailure { throwable ->
                    val appError = ErrorMapper.fromThrowable(throwable)
                    receiptError = appError.getDisplayMessage()
                }
            } finally {
                isGeneratingReceipt = false
            }
        }
    }

    fun viewReceipt(context: Context, cfeRepository: CfeRepository? = null) {
        val folio = (uiState as? CollectionDetailUiState.Success)?.collection?.folio ?: "CO-$documentoId"
        generateReceiptAndExecute(context, cfeRepository) { pdfFile ->
            PdfShareHelper.openPdf(context, pdfFile, title = "Recibo $folio")
        }
    }

    fun printReceipt(context: Context, cfeRepository: CfeRepository? = null) {
        val folio = (uiState as? CollectionDetailUiState.Success)?.collection?.folio ?: "CO-$documentoId"
        generateReceiptAndExecute(context, cfeRepository) { pdfFile ->
            PdfShareHelper.printPdf(context, pdfFile, jobName = "Recibo $folio")
        }
    }

    fun shareReceipt(context: Context, cfeRepository: CfeRepository? = null) {
        val folio = (uiState as? CollectionDetailUiState.Success)?.collection?.folio ?: "CO-$documentoId"
        generateReceiptAndExecute(context, cfeRepository) { pdfFile ->
            PdfShareHelper.sharePdf(
                context = context,
                pdfFile = pdfFile,
                subject = "Recibo de Cobro $folio",
                text = "Adjunto recibo de cobro $folio",
                chooserTitle = "Compartir recibo de cobro"
            )
        }
    }
}

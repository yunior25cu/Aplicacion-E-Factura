package com.authvex.balaxysefactura.ui.screens.budget.detail

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.BudgetRepository
import com.authvex.balaxysefactura.core.repository.CfeRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class BudgetDetailUiState {
    object Loading : BudgetDetailUiState()
    data class Success(val budget: BudgetDto) : BudgetDetailUiState()
    data class Error(val message: String) : BudgetDetailUiState()
}

sealed class BudgetActionEvent {
    object Idle : BudgetActionEvent()
    object Processing : BudgetActionEvent()
    data class ConfirmedSuccess(val message: String) : BudgetActionEvent()
    data class CancelledSuccess(val message: String) : BudgetActionEvent()
    data class InvoicedSuccess(val facturaId: Long, val message: String) : BudgetActionEvent()
    data class ActionError(val message: String) : BudgetActionEvent()
}

class BudgetDetailViewModel(
    private val budgetRepository: BudgetRepository,
    private val cfeRepository: CfeRepository? = null,
    val budgetId: Long
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    var uiState by mutableStateOf<BudgetDetailUiState>(BudgetDetailUiState.Loading)
        private set

    var actionEvent by mutableStateOf<BudgetActionEvent>(BudgetActionEvent.Idle)
        private set

    var isSharingPdf by mutableStateOf(false)
        private set

    // Invoicing Dialog State
    var invoiceOptions by mutableStateOf<BudgetInvoiceOptionsDto?>(null)
        private set

    var isOptionsLoading by mutableStateOf(false)
        private set

    private fun getTodayDate(): String = dateFormat.format(Date())

    var selectedPuntoVentaId by mutableStateOf<Long?>(null)
    var dialogFechaEmision by mutableStateOf(getTodayDate())
    var dialogFechaConfirmacion by mutableStateOf(getTodayDate())

    var cfeReferenceLabel by mutableStateOf<String?>(null)
        private set

    init {
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            uiState = BudgetDetailUiState.Loading
            val result = budgetRepository.getBudgetById(budgetId)
            result.onSuccess { budget ->
                uiState = BudgetDetailUiState.Success(budget)
                if (budget.factura != null) {
                    resolveLinkedCfe(budget.factura.id)
                }
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                uiState = BudgetDetailUiState.Error(appError.getDisplayMessage())
            }
        }
    }

    private suspend fun resolveLinkedCfe(facturaId: Long) {
        val repo = cfeRepository ?: return
        repo.getDocumentDetail(facturaId.toInt()).onSuccess { detail ->
            val label = if (!detail.serie.isNullOrBlank() && detail.numero != null && detail.numero > 0) {
                "CFE: ${detail.serie}-${detail.numero}"
            } else {
                "Factura CFE pendiente"
            }
            cfeReferenceLabel = label
        }.onFailure {
            cfeReferenceLabel = "Factura vinculada"
        }
    }

    fun loadInvoiceOptions() {
        viewModelScope.launch {
            isOptionsLoading = true
            dialogFechaEmision = getTodayDate()
            dialogFechaConfirmacion = getTodayDate()

            val result = budgetRepository.getInvoiceOptions(budgetId)
            result.onSuccess { options ->
                invoiceOptions = options
                val defaultPv = options.puntosVenta.find { it.esPredeterminado } ?: options.puntosVenta.firstOrNull()
                selectedPuntoVentaId = defaultPv?.id
            }.onFailure {
                invoiceOptions = BudgetInvoiceOptionsDto(esElectronico = false)
            }
            isOptionsLoading = false
        }
    }

    fun confirmBudget() {
        if (actionEvent is BudgetActionEvent.Processing) return
        actionEvent = BudgetActionEvent.Processing
        viewModelScope.launch {
            val result = budgetRepository.confirmBudget(budgetId)
            result.onSuccess {
                actionEvent = BudgetActionEvent.ConfirmedSuccess("Presupuesto confirmado correctamente")
                loadDetail()
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                actionEvent = BudgetActionEvent.ActionError(appError.getDisplayMessage())
            }
        }
    }

    fun cancelBudget() {
        if (actionEvent is BudgetActionEvent.Processing) return
        actionEvent = BudgetActionEvent.Processing
        viewModelScope.launch {
            val result = budgetRepository.cancelBudget(budgetId)
            result.onSuccess {
                actionEvent = BudgetActionEvent.CancelledSuccess("Presupuesto anulado correctamente")
                loadDetail()
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                actionEvent = BudgetActionEvent.ActionError(appError.getDisplayMessage())
            }
        }
    }

    fun sharePdf(context: Context, customCompany: EmpresaDto? = null) {
        if (isSharingPdf) return
        val currentBudget = (uiState as? BudgetDetailUiState.Success)?.budget ?: return

        isSharingPdf = true
        viewModelScope.launch {
            try {
                val company = customCompany ?: cfeRepository?.getEmpresa()?.getOrNull() ?: EmpresaDto(id = 1)
                val pdfResult = BudgetPdfGenerator.generate(context, currentBudget, company)

                pdfResult.onSuccess { pdfFile ->
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        pdfFile
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        putExtra(Intent.EXTRA_SUBJECT, "Presupuesto ${currentBudget.folio ?: currentBudget.id}")
                        putExtra(Intent.EXTRA_TEXT, "Adjunto presupuesto ${currentBudget.folio ?: currentBudget.id}")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    val chooser = Intent.createChooser(shareIntent, "Compartir presupuesto")
                    try {
                        context.startActivity(chooser)
                    } catch (_: Exception) {
                        actionEvent = BudgetActionEvent.ActionError("No hay aplicaciones disponibles para compartir el PDF.")
                    }
                }.onFailure { throwable ->
                    val appError = ErrorMapper.fromThrowable(throwable)
                    actionEvent = BudgetActionEvent.ActionError(appError.getDisplayMessage())
                }
            } catch (e: Exception) {
                actionEvent = BudgetActionEvent.ActionError("Error al generar PDF: ${e.message}")
            } finally {
                isSharingPdf = false
            }
        }
    }

    fun invoiceBudget(
        fechaEmision: String,
        fechaConfirmacion: String,
        puntoVentaIdFiscal: Long?,
        lineas: List<BudgetFacturarLineaDto>? = null
    ) {
        if (actionEvent is BudgetActionEvent.Processing) return

        if (fechaEmision.isBlank() || fechaConfirmacion.isBlank()) {
            actionEvent = BudgetActionEvent.ActionError("Las fechas de emisión y confirmación son obligatorias")
            return
        }

        if (fechaEmision > fechaConfirmacion) {
            actionEvent = BudgetActionEvent.ActionError("La fecha de emisión no puede ser posterior a la fecha de confirmación")
            return
        }

        if (invoiceOptions?.esElectronico == true && puntoVentaIdFiscal == null) {
            actionEvent = BudgetActionEvent.ActionError("Seleccione un punto de venta fiscal.")
            return
        }

        viewModelScope.launch {
            actionEvent = BudgetActionEvent.Processing

            val dto = BudgetFacturarDto(
                fechaEmision = fechaEmision,
                fechaConfirmacion = fechaConfirmacion,
                puntoVentaIdFiscal = puntoVentaIdFiscal,
                lineas = lineas
            )

            val result = budgetRepository.invoiceBudget(budgetId, dto)
            result.onSuccess { facturaId ->
                actionEvent = BudgetActionEvent.InvoicedSuccess(
                    facturaId = facturaId,
                    message = "Factura N° $facturaId generada correctamente desde el Presupuesto"
                )
                loadDetail()
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                actionEvent = BudgetActionEvent.ActionError(appError.getDisplayMessage())
            }
        }
    }

    fun resetActionEvent() {
        actionEvent = BudgetActionEvent.Idle
    }
}

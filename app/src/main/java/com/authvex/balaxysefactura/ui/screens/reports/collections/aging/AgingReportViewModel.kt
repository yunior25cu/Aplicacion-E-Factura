package com.authvex.balaxysefactura.ui.screens.reports.collections.aging

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.core.repository.CollectionReportRepository
import com.authvex.balaxysefactura.ui.screens.reports.DatePreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class AgingClientItem(
    val idCliente: Long,
    val clientName: String,
    val totalVencido: Double,
    val mainBucketLabel: String,
    val mainBucketAmount: Double
)

sealed class AgingReportUiState {
    object Loading : AgingReportUiState()
    data class Success(
        val items: List<CuentasPorCobrarAgingDto>,
        val sumNoVencido: Double,
        val sum1a30: Double,
        val sum31a60: Double,
        val sum61a90: Double,
        val sum91Mas: Double,
        val totalVencido: Double,
        val grandTotal: Double,
        val sumSaldoAFavor: Double,
        val clientRanking: List<AgingClientItem>,
        val hasFallback: Boolean
    ) : AgingReportUiState()
    data class Error(val message: String) : AgingReportUiState()
}

class AgingReportViewModel(
    private val reportRepository: CollectionReportRepository,
    private val cfeRepository: CfeRepository
) : ViewModel() {

    var uiState by mutableStateOf<AgingReportUiState>(AgingReportUiState.Loading)
        private set

    var selectedPreset by mutableStateOf(DatePreset.THIS_MONTH)
    var companyBaseCurrencyId by mutableStateOf<Int?>(null)
    var companyBaseCurrencyCode by mutableStateOf("UYU")
    var selectedCliente by mutableStateOf<ClienteDto?>(null)

    var clientesList by mutableStateOf<List<ClienteDto>>(emptyList())

    private var clientSearchJob: Job? = null

    init {
        loadInitialData()
    }

    private fun getPresetDates(preset: DatePreset): Pair<String, String> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val hasta = sdf.format(cal.time)

        return when (preset) {
            DatePreset.TODAY -> Pair(hasta, hasta)
            DatePreset.LAST_7_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -6)
                Pair(sdf.format(cal.time), hasta)
            }
            DatePreset.LAST_30_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -29)
                Pair(sdf.format(cal.time), hasta)
            }
            DatePreset.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                Pair(sdf.format(cal.time), hasta)
            }
            DatePreset.LAST_MONTH -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val d1 = sdf.format(cal.time)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val d2 = sdf.format(cal.time)
                Pair(d1, d2)
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            cfeRepository.getEmpresa().onSuccess { empresa ->
                companyBaseCurrencyId = empresa.moneda?.id
                companyBaseCurrencyCode = empresa.moneda?.codigo ?: "UYU"
            }

            cfeRepository.getClientes().onSuccess { clientesList = it }

            loadReport()
        }
    }

    fun loadReport() {
        viewModelScope.launch {
            uiState = AgingReportUiState.Loading
            val dates = getPresetDates(selectedPreset)

            val result = reportRepository.getAccountsReceivableAging(
                fechaDesde = dates.first,
                fechaHasta = dates.second,
                idCliente = selectedCliente?.id?.toLong(),
                idMoneda = null, // Backend aging amounts are ALWAYS in base currency!
                estado = 1
            )

            result.onSuccess { list ->
                val sumNoVencido = list.sumOf { it.totalNoVencidas }
                val sum1a30 = list.sumOf { it.totalVencidas1a30 }
                val sum31a60 = list.sumOf { it.totalVencidas31a60 }
                val sum61a90 = list.sumOf { it.totalVencidas61a90 }
                val sum91Mas = list.sumOf { it.totalVencidas91Mas }

                val totalOverdue = sum1a30 + sum31a60 + sum61a90 + sum91Mas
                val grandTotal = list.sumOf { it.total }
                val sumSaldoFavor = list.sumOf { it.saldoAFavor }
                val hasFallback = list.any { it.tieneFallbackVencimiento }

                val ranking = list.map { dto ->
                    val overdueClient = dto.totalVencidas1a30 + dto.totalVencidas31a60 + dto.totalVencidas61a90 + dto.totalVencidas91Mas
                    val (bucketLabel, bucketAmt) = when {
                        dto.totalVencidas91Mas > 0 -> "+91 días" to dto.totalVencidas91Mas
                        dto.totalVencidas61a90 > 0 -> "61–90 días" to dto.totalVencidas61a90
                        dto.totalVencidas31a60 > 0 -> "31–60 días" to dto.totalVencidas31a60
                        dto.totalVencidas1a30 > 0 -> "1–30 días" to dto.totalVencidas1a30
                        else -> "No vencido" to dto.totalNoVencidas
                    }
                    AgingClientItem(
                        idCliente = dto.idCliente,
                        clientName = dto.denominacionCliente ?: "Cliente N° ${dto.idCliente}",
                        totalVencido = overdueClient,
                        mainBucketLabel = bucketLabel,
                        mainBucketAmount = bucketAmt
                    )
                }.filter { it.totalVencido > 0 }.sortedByDescending { it.totalVencido }.take(10)

                uiState = AgingReportUiState.Success(
                    items = list,
                    sumNoVencido = sumNoVencido,
                    sum1a30 = sum1a30,
                    sum31a60 = sum31a60,
                    sum61a90 = sum61a90,
                    sum91Mas = sum91Mas,
                    totalVencido = totalOverdue,
                    grandTotal = grandTotal,
                    sumSaldoAFavor = sumSaldoFavor,
                    clientRanking = ranking,
                    hasFallback = hasFallback
                )
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                uiState = AgingReportUiState.Error(appError.getDisplayMessage())
            }
        }
    }

    fun onPresetSelected(preset: DatePreset) {
        selectedPreset = preset
        loadReport()
    }

    fun onClienteSelected(cliente: ClienteDto?) {
        selectedCliente = cliente
        loadReport()
    }

    fun onClientQueryChanged(query: String) {
        clientSearchJob?.cancel()
        clientSearchJob = viewModelScope.launch {
            delay(300)
            cfeRepository.getClientes(query).onSuccess { clientesList = it }
        }
    }
}

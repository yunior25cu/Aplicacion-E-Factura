package com.authvex.balaxysefactura.ui.screens.reports.collections.collected

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

sealed class CollectedReportUiState {
    object Loading : CollectedReportUiState()
    data class Success(
        val items: List<CobroSummaryDto>,
        val totalCobrado: Double,
        val cantidadCobros: Int,
        val ticketPromedio: Double
    ) : CollectedReportUiState()
    data class Error(val message: String) : CollectedReportUiState()
}

class CollectedReportViewModel(
    private val reportRepository: CollectionReportRepository,
    private val cfeRepository: CfeRepository
) : ViewModel() {

    var uiState by mutableStateOf<CollectedReportUiState>(CollectedReportUiState.Loading)
        private set

    var selectedPreset by mutableStateOf(DatePreset.THIS_MONTH)
    var companyBaseCurrencyId by mutableStateOf<Int?>(null)
    var selectedMoneda by mutableStateOf<TasaCambioSimpleDto?>(null)
    var selectedCliente by mutableStateOf<ClienteDto?>(null)

    var tasasCambioList by mutableStateOf<List<TasaCambioSimpleDto>>(emptyList())
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
            }

            cfeRepository.getClientes().onSuccess { clientesList = it }

            val dates = getPresetDates(selectedPreset)
            cfeRepository.getTasaCambios(dates.second).onSuccess { tasas ->
                tasasCambioList = tasas
                val baseId = companyBaseCurrencyId
                val matched = if (baseId != null) tasas.find { it.id == baseId } else null
                selectedMoneda = matched ?: tasas.firstOrNull()
            }

            loadReport()
        }
    }

    fun loadReport() {
        viewModelScope.launch {
            uiState = CollectedReportUiState.Loading
            val dates = getPresetDates(selectedPreset)

            val result = reportRepository.getCollectedSummaryExhaustive(
                fechaDesde = dates.first,
                fechaHasta = dates.second,
                idMoneda = selectedMoneda?.id?.toLong(),
                idCliente = selectedCliente?.id?.toLong()
            )

            result.onSuccess { list ->
                val isBaseCurrency = companyBaseCurrencyId == null || selectedMoneda?.id == companyBaseCurrencyId

                val sumTotal = list.sumOf { cobro ->
                    if (isBaseCurrency) {
                        cobro.montoTotalBase ?: cobro.total
                    } else {
                        cobro.montoTotalOriginal?.takeIf { it > 0 } ?: cobro.total
                    }
                }

                val count = list.size
                val avg = if (count > 0) sumTotal / count else 0.0

                uiState = CollectedReportUiState.Success(
                    items = list,
                    totalCobrado = sumTotal,
                    cantidadCobros = count,
                    ticketPromedio = avg
                )
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                uiState = CollectedReportUiState.Error(appError.getDisplayMessage())
            }
        }
    }

    fun onPresetSelected(preset: DatePreset) {
        selectedPreset = preset
        loadReport()
    }

    fun onMonedaSelected(moneda: TasaCambioSimpleDto) {
        selectedMoneda = moneda
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

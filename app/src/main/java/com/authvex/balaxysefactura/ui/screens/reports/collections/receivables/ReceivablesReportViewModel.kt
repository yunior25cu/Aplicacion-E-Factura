package com.authvex.balaxysefactura.ui.screens.reports.collections.receivables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.core.repository.CollectionReportRepository
import com.authvex.balaxysefactura.ui.screens.reports.DatePreset
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class ReceivablesReportUiState {
    object Loading : ReceivablesReportUiState()
    data class Success(
        val items: List<CuentasPorCobrarDto>,
        val totalPorCobrar: Double,
        val totalCobrado: Double,
        val totalNC: Double,
        val totalAjustes: Double,
        val totalSaldoAFavor: Double
    ) : ReceivablesReportUiState()
    data class Error(val message: String) : ReceivablesReportUiState()
}

class ReceivablesReportViewModel(
    private val reportRepository: CollectionReportRepository,
    private val cfeRepository: CfeRepository
) : ViewModel() {

    var uiState by mutableStateOf<ReceivablesReportUiState>(ReceivablesReportUiState.Loading)
        private set

    var selectedPreset by mutableStateOf(DatePreset.THIS_MONTH)
    var selectedStateFilter by mutableStateOf<Int?>(1) // Default 1 = Por cobrar
    var companyBaseCurrencyId by mutableStateOf<Int?>(null)
    var selectedMoneda by mutableStateOf<TasaCambioSimpleDto?>(null)
    var selectedCliente by mutableStateOf<ClienteDto?>(null)

    var tasasCambioList by mutableStateOf<List<TasaCambioSimpleDto>>(emptyList())
    var clientesList by mutableStateOf<List<ClienteDto>>(emptyList())

    private var clientSearchJob: kotlinx.coroutines.Job? = null

    fun onClientQueryChanged(query: String) {
        clientSearchJob?.cancel()
        clientSearchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            cfeRepository.getClientes(query).onSuccess { clientesList = it }
        }
    }

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
            uiState = ReceivablesReportUiState.Loading
            val dates = getPresetDates(selectedPreset)

            val result = reportRepository.getAccountsReceivable(
                fechaDesde = dates.first,
                fechaHasta = dates.second,
                idCliente = selectedCliente?.id?.toLong(),
                idMoneda = selectedMoneda?.id?.toLong(),
                estado = selectedStateFilter
            )

            result.onSuccess { list ->
                val isBaseCurrency = companyBaseCurrencyId == null || selectedMoneda?.id == companyBaseCurrencyId

                val sortedList = list.sortedByDescending { if (isBaseCurrency) it.porCobrar else it.porCobrarOriginal }

                val sumPorCobrar = sortedList.sumOf { if (isBaseCurrency) it.porCobrar else it.porCobrarOriginal }
                val sumCobrado = sortedList.sumOf { if (isBaseCurrency) it.importeCobrado else it.importeCobradoOriginal }
                val sumNC = sortedList.sumOf { if (isBaseCurrency) it.importeNotasCredito else it.importeNotasCreditoOriginal }
                val sumAjustes = sortedList.sumOf { if (isBaseCurrency) it.importeAjustado else it.importeAjustadoOriginal }
                val sumSaldoFavor = sortedList.sumOf { if (isBaseCurrency) it.saldoAFavor else it.saldoAFavorOriginal }

                uiState = ReceivablesReportUiState.Success(
                    items = sortedList,
                    totalPorCobrar = sumPorCobrar,
                    totalCobrado = sumCobrado,
                    totalNC = sumNC,
                    totalAjustes = sumAjustes,
                    totalSaldoAFavor = sumSaldoFavor
                )
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                uiState = ReceivablesReportUiState.Error(appError.getDisplayMessage())
            }
        }
    }

    fun onPresetSelected(preset: DatePreset) {
        selectedPreset = preset
        loadReport()
    }

    fun onStateFilterSelected(state: Int?) {
        selectedStateFilter = state
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
}

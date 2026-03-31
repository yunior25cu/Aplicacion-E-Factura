package com.authvex.balaxysefactura.ui.screens.reports

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.ReportsRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class ReportsUiState<out T> {
    object Loading : ReportsUiState<Nothing>()
    data class Success<T>(val data: T) : ReportsUiState<T>()
    data class Error(val message: String) : ReportsUiState<Nothing>()
    object Empty : ReportsUiState<Nothing>()
}

enum class ReportViewMode { LIST, CHART }
enum class SerieViewMode { BARS, LINE }

class ReportsViewModel(private val repository: ReportsRepository) : ViewModel() {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    var fechaDesde by mutableStateOf("")
    var fechaHasta by mutableStateOf("")

    // View Modes
    var serieViewMode by mutableStateOf(SerieViewMode.BARS)
    var clientesViewMode by mutableStateOf(ReportViewMode.LIST)
    var productosViewMode by mutableStateOf(ReportViewMode.LIST)
    var documentosViewMode by mutableStateOf(ReportViewMode.LIST)

    var resumenState by mutableStateOf<ReportsUiState<VentasResumenResponse>>(ReportsUiState.Loading)
    var serieState by mutableStateOf<ReportsUiState<VentasSerieResponse>>(ReportsUiState.Loading)
    var clientesState by mutableStateOf<ReportsUiState<VentasPorClienteResponse>>(ReportsUiState.Loading)
    var productosState by mutableStateOf<ReportsUiState<VentasPorProductoResponse>>(ReportsUiState.Loading)
    var documentosState by mutableStateOf<ReportsUiState<VentasPorDocumentoResponse>>(ReportsUiState.Loading)

    init {
        applyPreset(DatePreset.LAST_30_DAYS)
    }

    fun applyPreset(preset: DatePreset) {
        val calendar = Calendar.getInstance()
        val hasta = sdf.format(calendar.time)
        
        val desde = when (preset) {
            DatePreset.TODAY -> hasta
            DatePreset.LAST_7_DAYS -> {
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                sdf.format(calendar.time)
            }
            DatePreset.LAST_30_DAYS -> {
                calendar.add(Calendar.DAY_OF_YEAR, -29)
                sdf.format(calendar.time)
            }
            DatePreset.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                sdf.format(calendar.time)
            }
            DatePreset.LAST_MONTH -> {
                calendar.add(Calendar.MONTH, -1)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val d = sdf.format(calendar.time)
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                fechaHasta = sdf.format(calendar.time)
                return updateDateRange(d, fechaHasta)
            }
        }
        updateDateRange(desde, hasta)
    }

    fun updateDateRange(desde: String, hasta: String) {
        fechaDesde = desde
        fechaHasta = hasta
        loadAll()
    }

    fun loadAll() {
        loadResumen()
        loadSerie()
        loadClientes()
        loadProductos()
        loadDocumentos()
    }

    private fun loadResumen() {
        viewModelScope.launch {
            resumenState = ReportsUiState.Loading
            repository.getVentasResumen(fechaDesde, fechaHasta)
                .onSuccess { resumenState = ReportsUiState.Success(it) }
                .onFailure { resumenState = ReportsUiState.Error(it.message ?: "Error desconocido") }
        }
    }

    private fun loadSerie() {
        viewModelScope.launch {
            serieState = ReportsUiState.Loading
            repository.getVentasSerie(fechaDesde, fechaHasta)
                .onSuccess {
                    serieState = if (it.puntos.isEmpty()) ReportsUiState.Empty else ReportsUiState.Success(it)
                }
                .onFailure { serieState = ReportsUiState.Error(it.message ?: "Error desconocido") }
        }
    }

    private fun loadClientes() {
        viewModelScope.launch {
            clientesState = ReportsUiState.Loading
            repository.getVentasPorCliente(fechaDesde, fechaHasta)
                .onSuccess {
                    clientesState = if (it.items.isEmpty()) ReportsUiState.Empty else ReportsUiState.Success(it)
                }
                .onFailure { clientesState = ReportsUiState.Error(it.message ?: "Error desconocido") }
        }
    }

    private fun loadProductos() {
        viewModelScope.launch {
            productosState = ReportsUiState.Loading
            repository.getVentasPorProducto(fechaDesde, fechaHasta)
                .onSuccess {
                    productosState = if (it.items.isEmpty()) ReportsUiState.Empty else ReportsUiState.Success(it)
                }
                .onFailure { productosState = ReportsUiState.Error(it.message ?: "Error desconocido") }
        }
    }

    private fun loadDocumentos() {
        viewModelScope.launch {
            documentosState = ReportsUiState.Loading
            repository.getVentasPorDocumento(fechaDesde, fechaHasta)
                .onSuccess {
                    documentosState = if (it.items.isEmpty()) ReportsUiState.Empty else ReportsUiState.Success(it)
                }
                .onFailure { documentosState = ReportsUiState.Error(it.message ?: "Error desconocido") }
        }
    }
}

enum class DatePreset(val label: String) {
    TODAY("Hoy"),
    LAST_7_DAYS("Últimos 7 días"),
    LAST_30_DAYS("Últimos 30 días"),
    THIS_MONTH("Este mes"),
    LAST_MONTH("Mes anterior")
}

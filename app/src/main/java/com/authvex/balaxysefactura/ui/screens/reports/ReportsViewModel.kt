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

class ReportsViewModel(private val repository: ReportsRepository) : ViewModel() {

    var fechaDesde by mutableStateOf("")
    var fechaHasta by mutableStateOf("")

    var resumenState by mutableStateOf<ReportsUiState<VentasResumenResponse>>(ReportsUiState.Loading)
    var serieState by mutableStateOf<ReportsUiState<VentasSerieResponse>>(ReportsUiState.Loading)
    var clientesState by mutableStateOf<ReportsUiState<VentasPorClienteResponse>>(ReportsUiState.Loading)
    var productosState by mutableStateOf<ReportsUiState<VentasPorProductoResponse>>(ReportsUiState.Loading)
    var documentosState by mutableStateOf<ReportsUiState<VentasPorDocumentoResponse>>(ReportsUiState.Loading)

    init {
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fechaHasta = sdf.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        fechaDesde = sdf.format(calendar.time)
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

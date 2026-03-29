package com.authvex.balaxysefactura.ui.screens.emission

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class EmissionUiState {
    object Idle : EmissionUiState()
    object LoadingCatalogs : EmissionUiState()
    data class FormReady(
        val clientes: List<ClienteDto>,
        val productos: List<ProductoDto>,
        val monedas: List<CatalogoItemDto>,
        val almacenes: List<CatalogoItemDto>,
        val formasPago: List<CatalogoItemDto>,
        val vencimientos: List<CatalogoItemDto>,
        val listasPrecio: List<CatalogoItemDto>
    ) : EmissionUiState()
    data class Processing(val message: String) : EmissionUiState()
    data class Success(val documentoId: Long, val message: String) : EmissionUiState()
    data class Error(val error: AppError) : EmissionUiState()
}

data class LineaForm(
    val producto: ProductoDto,
    val cantidad: Double,
    val precioUnitario: Double,
    val descuento: Double = 0.0
)

enum class FiscalIntent(val code: Int, val label: String) {
    E_FACTURA(111, "e-Factura"),
    E_TICKET(101, "e-Ticket")
}

data class FiscalResolution(
    val puntoVentaId: Int,
    val cfeCode: Int,
    val serie: String?,
    val proximoNumero: Long?,
    val name: String
)

class EmissionViewModel(private val repository: CfeRepository) : ViewModel() {

    var uiState by mutableStateOf<EmissionUiState>(EmissionUiState.Idle)
        private set

    // 1. Intención Fiscal (Pre-selección de usuario)
    var selectedFiscalIntent by mutableStateOf(FiscalIntent.E_FACTURA)

    // 2. Form State
    var selectedCliente by mutableStateOf<ClienteDto?>(null)
    var selectedMoneda by mutableStateOf<CatalogoItemDto?>(null)
    var selectedAlmacen by mutableStateOf<CatalogoItemDto?>(null)
    var selectedFormaPago by mutableStateOf<CatalogoItemDto?>(null)
    var selectedVencimiento by mutableStateOf<CatalogoItemDto?>(null)
    var selectedListaPrecio by mutableStateOf<CatalogoItemDto?>(null)
    val lineas = mutableStateListOf<LineaForm>()
    var notas by mutableStateOf("")

    // 3. Resolución Fiscal (Post-validación backend)
    var isFiscalResolved by mutableStateOf(false)
    var availableFiscalOptions by mutableStateOf<List<CfeTipoPermitidoDto>>(emptyList())
    var selectedResolution by mutableStateOf<FiscalResolution?>(null)
    var resolutionError by mutableStateOf<String?>(null)
    
    private var createdDocumentoId: Long? = null
    private var catalogData: EmissionUiState.FormReady? = null

    init {
        loadCatalogs()
    }

    fun loadCatalogs() {
        viewModelScope.launch {
            uiState = EmissionUiState.LoadingCatalogs
            try {
                val clientes = repository.getClientes().getOrThrow()
                val productos = repository.getProductos().getOrThrow()
                val monedas = repository.getMonedas().getOrThrow()
                val almacenes = repository.getAlmacenes().getOrThrow()
                val formasPago = repository.getFormasPago().getOrThrow()
                val vencimientos = repository.getVencimientos().getOrNull() ?: emptyList()
                val listasPrecio = repository.getListasPrecio().getOrNull() ?: emptyList()

                catalogData = EmissionUiState.FormReady(
                    clientes, productos, monedas, almacenes, formasPago, vencimientos, listasPrecio
                )
                uiState = catalogData!!

                if (selectedMoneda == null && monedas.isNotEmpty()) selectedMoneda = monedas.first()
                if (selectedAlmacen == null && almacenes.isNotEmpty()) selectedAlmacen = almacenes.first()
                if (selectedFormaPago == null && formasPago.isNotEmpty()) selectedFormaPago = formasPago.first()
            } catch (e: Exception) {
                uiState = EmissionUiState.Error(
                    if (e is AppError) e else AppError.Unexpected(e.message ?: "Error al cargar catálogos")
                )
            }
        }
    }

    fun addLinea(producto: ProductoDto, cantidad: Double) {
        lineas.add(LineaForm(producto, cantidad, producto.precio ?: 0.0))
    }

    fun removeLinea(linea: LineaForm) {
        lineas.remove(linea)
    }

    fun startEmissionFlow() {
        if (uiState is EmissionUiState.Processing) return

        val cliente = selectedCliente ?: return
        val moneda = selectedMoneda ?: return
        val almacen = selectedAlmacen ?: return
        val formaPago = selectedFormaPago ?: return
        if (lineas.isEmpty()) return

        viewModelScope.launch {
            uiState = EmissionUiState.Processing("Preparando documento...")
            val request = FacturaRequest(
                clienteId = cliente.id,
                monedaId = moneda.id,
                fecha = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                almacenId = almacen.id,
                formaPagoId = formaPago.id,
                vencimientoId = selectedVencimiento?.id,
                listaPrecioId = selectedListaPrecio?.id,
                lineas = lineas.map { 
                    FacturaLineaRequest(it.producto.id, it.cantidad, it.precioUnitario, it.descuento)
                },
                notas = notas
            )

            repository.createFactura(request).onSuccess { documentoId ->
                createdDocumentoId = documentoId
                uiState = EmissionUiState.Processing("Validando datos...")
                repository.getFactura(documentoId).onSuccess { _ ->
                    uiState = EmissionUiState.Processing("Consultando configuración fiscal...")
                    repository.getTiposPermitidos().onSuccess { tipos ->
                        resolveFiscalIntent(documentoId, tipos)
                    }.onFailure { handleFailure(it) }
                }.onFailure { handleFailure(it) }
            }.onFailure { handleFailure(it) }
        }
    }

    private fun resolveFiscalIntent(documentoId: Long, allOptions: List<CfeTipoPermitidoDto>) {
        val compatibleOptions = allOptions.filter { 
            it.implementedInUi && it.code == selectedFiscalIntent.code 
        }

        if (compatibleOptions.isEmpty()) {
            uiState = catalogData!!
            isFiscalResolved = false
            resolutionError = "No hay configuración fiscal válida para emitir este tipo de comprobante"
            return
        }

        availableFiscalOptions = compatibleOptions
        resolutionError = null

        if (compatibleOptions.size == 1) {
            val option = compatibleOptions.first()
            confirmResolution(option)
            executeFinalEmit(documentoId, option)
        } else {
            val suggested = compatibleOptions.find { it.suggested }
            if (suggested != null) {
                confirmResolution(suggested)
            }
            uiState = catalogData!!
            isFiscalResolved = true
        }
    }

    fun confirmResolution(option: CfeTipoPermitidoDto) {
        selectedResolution = FiscalResolution(
            puntoVentaId = option.puntoVentaId,
            cfeCode = option.code,
            serie = option.serie,
            proximoNumero = option.proximoNumero,
            name = option.name
        )
    }

    fun onFinalEmitClick() {
        val docId = createdDocumentoId ?: return
        val resolution = selectedResolution ?: return
        
        val option = availableFiscalOptions.find { 
            it.puntoVentaId == resolution.puntoVentaId && it.code == resolution.cfeCode 
        } ?: return

        executeFinalEmit(docId, option)
    }

    private fun executeFinalEmit(documentoId: Long, option: CfeTipoPermitidoDto) {
        viewModelScope.launch {
            uiState = EmissionUiState.Processing("Emitiendo fiscalmente...")
            val emitRequest = CfeEmitRequest(
                puntoVentaId = option.puntoVentaId,
                seriePreferida = option.serie,
                cfeCode = option.code,
                ncAdjustmentMode = null
            )
            
            repository.emitCfe(documentoId, emitRequest).onSuccess {
                uiState = EmissionUiState.Processing("Consultando estado final...")
                repository.getCfeStatus(documentoId).onSuccess { status ->
                    uiState = EmissionUiState.Success(
                        documentoId = documentoId,
                        message = status.mensaje ?: "Documento emitido correctamente."
                    )
                }.onFailure { handleFailure(it) }
            }.onFailure { handleFailure(it) }
        }
    }

    private fun handleFailure(error: Throwable) {
        uiState = EmissionUiState.Error(
            if (error is AppError) error else AppError.Unexpected(error.message ?: "Error desconocido")
        )
    }
    
    fun resetToForm() {
        lineas.clear()
        notas = ""
        isFiscalResolved = false
        availableFiscalOptions = emptyList()
        selectedResolution = null
        resolutionError = null
        createdDocumentoId = null
        uiState = catalogData ?: EmissionUiState.Idle
    }
}

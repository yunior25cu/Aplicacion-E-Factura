package com.authvex.balaxysefactura.ui.screens.emission

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class EmissionUiState {
    object LoadingInitial : EmissionUiState()
    data class SelectPOS(val puntosVenta: List<PuntoVentaDto>) : EmissionUiState()
    data class SelectType(val types: List<CfeFiscalDocumentAvailabilityItemDto>) : EmissionUiState()
    data class FillForm(
        val type: CfeFiscalDocumentAvailabilityItemDto,
        val catalogs: CatalogData
    ) : EmissionUiState()
    data class Processing(val message: String) : EmissionUiState()
    data class Success(val documentoId: Long, val message: String) : EmissionUiState()
    data class Error(val error: AppError) : EmissionUiState()
}

data class CatalogData(
    val monedas: List<CatalogoItemDto>,
    val almacenes: List<CatalogoItemDto>,
    val formasPago: List<CatalogoItemDto>,
    val vencimientos: List<CatalogoItemDto>,
    val listasPrecio: List<CatalogoItemDto>,
    val indicadoresC4: List<CfeFiscalIndicadorFacturacionDto>
)

data class LineaForm(
    val producto: ProductoDto,
    var cantidad: Double,
    var precioUnitario: Double,
    val descuento: Double = 0.0,
    var indicadorFacturacionC4: Int? = null,
    var indicadorFacturacionC4Sugerido: Int? = null,
    var indicadorFacturacionC4SugeridoLabel: String? = null
)

class EmissionViewModel(private val repository: CfeRepository) : ViewModel() {

    var uiState by mutableStateOf<EmissionUiState>(EmissionUiState.LoadingInitial)
        private set

    // Selection State
    var selectedPOS by mutableStateOf<PuntoVentaDto?>(null)
    var selectedFiscalType by mutableStateOf<CfeFiscalDocumentAvailabilityItemDto?>(null)

    // Form State (Common)
    var selectedCliente by mutableStateOf<ClienteDto?>(null)
    var selectedMoneda by mutableStateOf<CatalogoItemDto?>(null)
    var selectedAlmacen by mutableStateOf<CatalogoItemDto?>(null)
    var selectedFormaPago by mutableStateOf<CatalogoItemDto?>(null)
    var selectedVencimiento by mutableStateOf<CatalogoItemDto?>(null)
    var selectedListaPrecio by mutableStateOf<CatalogoItemDto?>(null)
    val lineas = mutableStateListOf<LineaForm>()
    var notas by mutableStateOf("")

    // Search State
    var clientSearchResults by mutableStateOf<List<ClienteDto>>(emptyList())
    var productSearchResults by mutableStateOf<List<ProductoDto>>(emptyList())
    var isSearching by mutableStateOf(false)
    private var searchJob: Job? = null

    // Form State (Returns/NC/ND)
    var idDocumentoOrigen by mutableStateOf<Long?>(null)
    
    // Line Configuration State
    var productBeingConfigured by mutableStateOf<ProductoDto?>(null)
    var isConfiguringLine by mutableStateOf(false)
    var lineConfigurationSugerido by mutableStateOf<CfeFiscalIndicadorSugeridoDto?>(null)
    var isResolvingC4 by mutableStateOf(false)

    private var cachedCatalogs: CatalogData? = null

    init {
        loadPuntosVenta()
    }

    fun loadPuntosVenta() {
        viewModelScope.launch {
            uiState = EmissionUiState.LoadingInitial
            repository.getPuntosVenta().onSuccess { pvs ->
                uiState = EmissionUiState.SelectPOS(pvs)
            }.onFailure { handleFailure(it) }
        }
    }

    fun selectPOS(pv: PuntoVentaDto) {
        selectedPOS = pv
        viewModelScope.launch {
            uiState = EmissionUiState.Processing("Consultando documentos habilitados...")
            repository.getDocumentosHabilitados(pv.id).onSuccess { groups ->
                val items = groups.firstOrNull()?.items ?: emptyList()
                uiState = EmissionUiState.SelectType(items)
            }.onFailure { handleFailure(it) }
        }
    }

    fun selectFiscalType(item: CfeFiscalDocumentAvailabilityItemDto) {
        selectedFiscalType = item
        if (cachedCatalogs != null) {
            uiState = EmissionUiState.FillForm(item, cachedCatalogs!!)
        } else {
            loadCatalogsAndGoToForm(item)
        }
    }

    private fun loadCatalogsAndGoToForm(item: CfeFiscalDocumentAvailabilityItemDto) {
        viewModelScope.launch {
            uiState = EmissionUiState.Processing("Cargando catálogos...")
            try {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val data = CatalogData(
                    monedas = repository.getMonedas().getOrThrow(),
                    almacenes = repository.getAlmacenes().getOrThrow(),
                    formasPago = repository.getFormasPago().getOrThrow(),
                    vencimientos = repository.getVencimientos().getOrNull() ?: emptyList(),
                    listasPrecio = repository.getListasPrecio().getOrNull() ?: emptyList(),
                    indicadoresC4 = repository.getIndicadoresFacturacion(
                        item.cfeCode, item.puntoVentaId, item.serie, today
                    ).getOrNull() ?: emptyList()
                )
                cachedCatalogs = data
                
                if (selectedMoneda == null) selectedMoneda = data.monedas.find { it.codigo == "UYU" } ?: data.monedas.firstOrNull()
                if (selectedAlmacen == null) selectedAlmacen = data.almacenes.firstOrNull()
                if (selectedFormaPago == null) selectedFormaPago = data.formasPago.firstOrNull()

                uiState = EmissionUiState.FillForm(item, data)
            } catch (e: Exception) {
                handleFailure(e)
            }
        }
    }

    fun searchClients(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            isSearching = true
            repository.getClientes(query).onSuccess {
                clientSearchResults = it
            }
            isSearching = false
        }
    }

    fun searchProducts(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            isSearching = true
            repository.getProductos(query).onSuccess {
                productSearchResults = it
            }
            isSearching = false
        }
    }

    fun startLineConfiguration(producto: ProductoDto) {
        val type = selectedFiscalType ?: return
        val pos = selectedPOS ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        
        productBeingConfigured = producto
        isResolvingC4 = true
        isConfiguringLine = true
        
        viewModelScope.launch {
            repository.getIndicadorSugerido(
                cfeCode = type.cfeCode,
                tasaIva = producto.tasaIva ?: 0.0,
                currentValue = null,
                puntoVentaId = pos.id,
                seriePreferida = type.serie,
                fechaEmision = today
            ).onSuccess {
                lineConfigurationSugerido = it
            }.onFailure {
                lineConfigurationSugerido = null
            }
            isResolvingC4 = false
        }
    }

    fun confirmLineConfiguration(cantidad: Double, precio: Double, indicadorC4: Int?, sugerido: Int? = null, label: String? = null) {
        val producto = productBeingConfigured ?: return
        lineas.add(LineaForm(
            producto = producto, 
            cantidad = cantidad, 
            precioUnitario = precio, 
            indicadorFacturacionC4 = indicadorC4,
            indicadorFacturacionC4Sugerido = sugerido,
            indicadorFacturacionC4SugeridoLabel = label
        ))
        cancelLineConfiguration()
    }
    
    fun cancelLineConfiguration() {
        productBeingConfigured = null
        isConfiguringLine = false
        lineConfigurationSugerido = null
        isResolvingC4 = false
    }

    fun removeLinea(index: Int) {
        if (index in lineas.indices) {
            lineas.removeAt(index)
        }
    }

    fun proceedToEmission() {
        val type = selectedFiscalType ?: return
        val pos = selectedPOS ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        
        viewModelScope.launch {
            // 1. Precheck CAE
            uiState = EmissionUiState.Processing("Verificando salud de CAE...")
            val precheck = repository.caePrecheck(pos.id, type.cfeCode, type.serie, today).getOrNull()
            if (precheck != null && !precheck.hasValidCae) {
                uiState = EmissionUiState.Error(AppError.Validation(precheck.message ?: "CAE no válido o vencido"))
                return@launch
            }

            // 2. Create ERP
            uiState = EmissionUiState.Processing("Creando documento ERP...")
            val result = if (isVenta(type.cfeCode)) {
                createFacturaERP(today)
            } else {
                createDevolucionERP(type.cfeCode, today)
            }

            result.onSuccess { documentoId ->
                validateAndEmit(documentoId, type, pos)
            }.onFailure { handleFailure(it) }
        }
    }

    private suspend fun createFacturaERP(today: String): Result<Long> {
        val tasaCambio = 1.0 
        val isBaseCurrency = (tasaCambio == 1.0)
        
        val mappedLineas = mapLineas(isBaseCurrency)
        
        val importeBase = mappedLineas.sumOf { it.importeBase }
        val iva = mappedLineas.sumOf { it.iva }

        val request = FacturaCreateDto(
            fechaEmision = today,
            fechaConfirmacion = today,
            idMoneda = selectedMoneda?.id ?: 0,
            tasaCambio = tasaCambio,
            importeBase = importeBase,
            iva = iva,
            importeTotalBase = importeBase + iva,
            importeOriginal = if (isBaseCurrency) 0.0 else importeBase,
            ivaOriginal = if (isBaseCurrency) 0.0 else iva,
            importeTotalOriginal = if (isBaseCurrency) 0.0 else (importeBase + iva),
            idAlmacen = selectedAlmacen?.id ?: 0,
            idCliente = selectedCliente?.id ?: 0,
            documentoProductos = mappedLineas,
            nota = notas,
            esElectronico = true,
            idFormaPago = selectedFormaPago?.id,
            idVencimiento = selectedVencimiento?.id,
            idListaPrecio = selectedListaPrecio?.id
        )
        return repository.createFactura(request)
    }

    private suspend fun createDevolucionERP(cfeCode: Int, today: String): Result<Long> {
        if (idDocumentoOrigen == null) return Result.failure(Exception("Debe seleccionar un documento de origen"))
        
        val tasaCambio = 1.0
        val isBaseCurrency = (tasaCambio == 1.0)
        
        val mappedLineas = mapLineas(isBaseCurrency)
        val importeBase = mappedLineas.sumOf { it.importeBase }
        val iva = mappedLineas.sumOf { it.iva }

        val naturaleza = when (cfeCode) {
            102, 112 -> "Credito"
            103, 113 -> "Debito"
            else -> "Credito"
        }

        val request = DevolucionCreateDto(
            fechaEmision = today,
            fechaConfirmacion = today,
            idMoneda = selectedMoneda?.id ?: 0,
            tasaCambio = tasaCambio,
            importeBase = importeBase,
            iva = iva,
            importeTotalBase = importeBase + iva,
            importeOriginal = if (isBaseCurrency) 0.0 else importeBase,
            ivaOriginal = if (isBaseCurrency) 0.0 else iva,
            importeTotalOriginal = if (isBaseCurrency) 0.0 else (importeBase + iva),
            idAlmacen = selectedAlmacen?.id ?: 0,
            idCliente = selectedCliente?.id ?: 0,
            documentoProductos = mappedLineas,
            nota = notas,
            esElectronico = true,
            tipoDevolucion = "Factura",
            naturalezaNota = naturaleza,
            idDocumentoOrigen = idDocumentoOrigen!!,
            idFormaPago = selectedFormaPago?.id,
            idVencimiento = selectedVencimiento?.id,
            idListaPrecio = selectedListaPrecio?.id
        )
        return repository.createDevolucion(request)
    }

    private fun mapLineas(isBaseCurrency: Boolean): List<FacturaLineaRequest> {
        return lineas.map { 
            val price = it.precioUnitario
            val qty = it.cantidad
            val taxRate = it.producto.tasaIva ?: 0.0
            
            val importeBase = price * qty
            val iva = importeBase * taxRate
            val totalConIva = importeBase + iva
            
            val normalizedC4 = when (it.indicadorFacturacionC4) {
                null, 1, 2, 3, 4 -> null
                else -> it.indicadorFacturacionC4
            }
            
            FacturaLineaRequest(
                idProducto = it.producto.id,
                cantidad = qty,
                precioBase = price,
                importeBase = importeBase,
                iva = iva,
                descuento = it.descuento,
                ivaOriginal = if (isBaseCurrency) 0.0 else iva,
                descuentoOriginal = if (isBaseCurrency) 0.0 else it.descuento,
                precioBaseConIva = price * (1 + taxRate),
                importeBaseConIva = totalConIva,
                precioOriginal = if (isBaseCurrency) 0.0 else price,
                importeOriginal = if (isBaseCurrency) 0.0 else importeBase,
                precioOriginalConIva = if (isBaseCurrency) 0.0 else (price * (1 + taxRate)),
                importeOriginalConIva = if (isBaseCurrency) 0.0 else totalConIva,
                indicadorFacturacionC4 = normalizedC4
            )
        }
    }

    private suspend fun validateAndEmit(documentoId: Long, item: CfeFiscalDocumentAvailabilityItemDto, pos: PuntoVentaDto) {
        uiState = EmissionUiState.Processing("Validando comprobante...")
        repository.validateCfe(documentoId, item.cfeCode, pos.id, item.serie).onSuccess { valRes ->
            if (!valRes.isValid) {
                val errorMsg = if (valRes.errors.isNotEmpty()) valRes.errors.joinToString("\n") else "Error de validación fiscal"
                uiState = EmissionUiState.Error(AppError.Validation(errorMsg))
                return
            }

            uiState = EmissionUiState.Processing("Emitiendo fiscalmente...")
            val emitReq = CfeEmitRequest(
                puntoVentaId = pos.id,
                seriePreferida = item.serie,
                cfeCode = item.cfeCode,
                ncAdjustmentMode = null
            )
            
            repository.emitCfe(documentoId, emitReq).onSuccess {
                startPolling(documentoId)
            }.onFailure { handleFailure(it) }
        }.onFailure { handleFailure(it) }
    }

    private fun startPolling(documentoId: Long) {
        viewModelScope.launch {
            uiState = EmissionUiState.Processing("Consultando estado final...")
            repository.getCfeStatus(documentoId).onSuccess { status ->
                uiState = EmissionUiState.Success(
                    documentoId = documentoId,
                    message = status.mensaje ?: "Emisión completada."
                )
            }.onFailure { handleFailure(it) }
        }
    }

    private fun isVenta(cfeCode: Int) = cfeCode == 101 || cfeCode == 111

    private fun handleFailure(error: Throwable) {
        val appError = if (error is AppError) error else AppError.Unexpected(error.message ?: "Error desconocido")
        uiState = EmissionUiState.Error(appError)
    }

    fun resetToStart() {
        selectedPOS = null
        selectedFiscalType = null
        resetForm()
        loadPuntosVenta()
    }

    private fun resetForm() {
        selectedCliente = null
        lineas.clear()
        notas = ""
        idDocumentoOrigen = null
        clientSearchResults = emptyList()
        productSearchResults = emptyList()
        cancelLineConfiguration()
    }
}

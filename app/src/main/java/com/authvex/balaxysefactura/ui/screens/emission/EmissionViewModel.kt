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
        val pos: PuntoVentaDto,
        val catalogs: CatalogData
    ) : EmissionUiState()
    data class Processing(val message: String) : EmissionUiState()
    data class Success(val documentoId: Long, val message: String) : EmissionUiState()
    data class Error(val error: AppError) : EmissionUiState()
}

data class CatalogData(
    val tasaCambios: List<TasaCambioSimpleDto>,
    val almacenes: List<CatalogoItemDto>,
    val vencimientos: List<CatalogoItemDto>,
    val listasPrecio: List<CatalogoItemDto>,
    val indicadoresC4: List<CfeFiscalIndicadorFacturacionDto>
)

data class LineaForm(
    val producto: ProductoDto,
    var cantidad: Double,
    var precioUnitario: Double,
    val descuento: Double = 0.0,
    var idSkuVariante: Long? = null,
    var indicadorFacturacionC4: Int? = null,
    var indicadorFacturacionC4Sugerido: Int? = null,
    var indicadorFacturacionC4SugeridoLabel: String? = null
)

class EmissionViewModel(private val repository: CfeRepository) : ViewModel() {

    var uiState by mutableStateOf<EmissionUiState>(EmissionUiState.LoadingInitial)
        private set

    // Fiscal Route State
    var selectedPOS by mutableStateOf<PuntoVentaDto?>(null)
    var selectedFiscalType by mutableStateOf<CfeFiscalDocumentAvailabilityItemDto?>(null)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // Form State (Common)
    var fechaEmision by mutableStateOf(getTodayDate())
    var fechaConfirmacion by mutableStateOf(getTodayDate())
    var preciosIncluyenIva by mutableStateOf(false)

    var baseCurrencyId by mutableStateOf<Int?>(null)
    var tasaCambioConfig by mutableStateOf<TasaCambioConfigDto?>(null)
    var isLoadingExchangeRate by mutableStateOf(false)
    var exchangeRateError by mutableStateOf<String?>(null)

    var selectedCliente by mutableStateOf<ClienteDto?>(null)
    var selectedMoneda by mutableStateOf<TasaCambioSimpleDto?>(null)
    var selectedAlmacen by mutableStateOf<CatalogoItemDto?>(null)
    var selectedCondicionPago by mutableStateOf(CondicionPagoComercial.CONTADO)
    var selectedVencimiento by mutableStateOf<CatalogoItemDto?>(null)
    var selectedListaPrecio by mutableStateOf<CatalogoItemDto?>(null)
    val lineas = mutableStateListOf<LineaForm>()
    var notas by mutableStateOf("")

    // Search State
    var clientSearchResults by mutableStateOf<List<ClienteDto>>(emptyList())
    var productSearchResults by mutableStateOf<List<ProductoDto>>(emptyList())
    var isSearching by mutableStateOf(false)
    private var searchJob: Job? = null

    // Form State (Returns/NC/ND - Origin Document Selection)
    var idDocumentoOrigen by mutableStateOf<Long?>(null)
    var selectedOriginCfe by mutableStateOf<CfeSummaryDto?>(null)
    var selectedOriginDoc by mutableStateOf<BudgetDto?>(null)
    var originCfeSearchResults by mutableStateOf<List<CfeSummaryDto>>(emptyList())
    var isSearchingOriginCfe by mutableStateOf(false)
        private set

    // Line Configuration & Edit State
    var productBeingConfigured by mutableStateOf<ProductoDto?>(null)
    var isConfiguringLine by mutableStateOf(false)
    var editingLineIndex by mutableStateOf<Int?>(null)
    var dialogQuantityText by mutableStateOf("1")
    var dialogUnitPriceText by mutableStateOf("0")
    var lineDialogError by mutableStateOf<String?>(null)
    var lineConfigurationSugerido by mutableStateOf<CfeFiscalIndicadorSugeridoDto?>(null)
    var isResolvingC4 by mutableStateOf(false)

    var cachedCatalogs: CatalogData? = null
        private set

    init {
        loadPuntosVenta()
    }

    private fun getTodayDate(): String = dateFormat.format(Date())

    fun isBaseCurrency(): Boolean {
        val selId = selectedMoneda?.id
        val baseId = baseCurrencyId
        return selId != null && baseId != null && selId == baseId
    }

    fun isOriginRequired(): Boolean {
        val code = selectedFiscalType?.cfeCode ?: 0
        return code == 102 || code == 103 || code == 112 || code == 113
    }

    fun resolveOriginCfeCode(): Int {
        return when (selectedFiscalType?.cfeCode) {
            102, 103 -> 101 // NC/ND e-Ticket references e-Ticket 101
            112, 113 -> 111 // NC/ND e-Factura references e-Factura 111
            else -> 101
        }
    }

    fun searchOriginCfes(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            isSearchingOriginCfe = true
            val originCode = resolveOriginCfeCode()
            repository.searchDocuments(
                query = query.ifBlank { null },
                page = 1
            ).onSuccess { response ->
                originCfeSearchResults = response.items.filter { it.cfeCode == originCode }
            }.onFailure {
                originCfeSearchResults = emptyList()
            }
            isSearchingOriginCfe = false
        }
    }

    fun selectOriginDocument(cfe: CfeSummaryDto) {
        selectedOriginCfe = cfe
        viewModelScope.launch {
            uiState = EmissionUiState.Processing("Cargando documento origen...")
            repository.getFacturaById(cfe.documentoId.toLong()).onSuccess { saleDoc ->
                selectedOriginDoc = saleDoc
                idDocumentoOrigen = saleDoc.id

                selectedCliente = saleDoc.cliente
                selectedAlmacen = saleDoc.almacen
                preciosIncluyenIva = saleDoc.preciosIncluyenIva

                val docCurrency = saleDoc.moneda
                if (docCurrency != null) {
                    selectedMoneda = TasaCambioSimpleDto(
                        id = docCurrency.id,
                        codigo = docCurrency.codigo ?: "UYU",
                        denominacion = docCurrency.nombre,
                        simbolo = "$",
                        decimales = 2,
                        tasaPromedio = saleDoc.tasaCambio
                    )
                    exchangeRateError = null
                }

                val isBase = isBaseCurrency()
                val newLines = saleDoc.documentoProductos.map { item ->
                    val unitPrice = if (isBase) item.precioBase else item.precioOriginal
                    val prod = item.producto ?: ProductoDto(
                        id = item.idProducto?.toInt() ?: 0,
                        nombre = item.descripcion ?: "Producto",
                        precio = unitPrice,
                        tasaIva = item.porcentajeIva
                    )
                    LineaForm(
                        producto = prod,
                        cantidad = item.cantidad,
                        precioUnitario = unitPrice,
                        indicadorFacturacionC4 = item.indicadorFacturacionC4
                    )
                }

                lineas.clear()
                lineas.addAll(newLines)

                val type = selectedFiscalType
                val pos = selectedPOS
                val catalogs = cachedCatalogs
                if (type != null && pos != null && catalogs != null) {
                    uiState = EmissionUiState.FillForm(type, pos, catalogs)
                }
            }.onFailure { error ->
                handleFailure(error)
            }
        }
    }

    fun clearOriginDocument() {
        selectedOriginCfe = null
        selectedOriginDoc = null
        idDocumentoOrigen = null
        lineas.clear()
        selectedCliente = null
    }

    fun onNotasChanged(value: String) {
        notas = InvoiceNoteSanitizer.sanitizeInvoiceNote(value)
    }

    fun onFechaConfirmacionChanged(newDate: String) {
        fechaConfirmacion = newDate
        if (isOriginRequired() && idDocumentoOrigen != null) {
            // Retain historical rate from origin document for NC/ND
            return
        }
        isLoadingExchangeRate = true
        exchangeRateError = null
        viewModelScope.launch {
            repository.getTasaCambios(newDate).onSuccess { tasas ->
                cachedCatalogs = cachedCatalogs?.copy(tasaCambios = tasas)
                val currentMonedaId = selectedMoneda?.id
                val matched = tasas.find { it.id == currentMonedaId }
                if (matched != null) {
                    selectedMoneda = matched
                    if (!isBaseCurrency() && matched.tasaPromedio <= 0.0) {
                        exchangeRateError = "No se ha definido una tasa de cambio vigente para la moneda seleccionada a la fecha de confirmación."
                    }
                } else {
                    selectedMoneda = selectedMoneda?.copy(tasaPromedio = 0.0)
                    if (!isBaseCurrency()) {
                        exchangeRateError = "No se ha definido una tasa de cambio vigente para la moneda seleccionada a la fecha de confirmación."
                    }
                }
            }.onFailure {
                exchangeRateError = "Error al consultar las tasas de cambio para la fecha seleccionada."
            }
            isLoadingExchangeRate = false
        }
    }

    fun onMonedaSelected(item: TasaCambioSimpleDto) {
        val previousMonedaId = selectedMoneda?.id
        selectedMoneda = item

        if (previousMonedaId != null && previousMonedaId != item.id) {
            // Reset line unit prices on currency change so user inputs price in new currency
            lineas.forEachIndexed { idx, line ->
                lineas[idx] = line.copy(precioUnitario = 0.0)
            }
        }

        if (isBaseCurrency()) {
            exchangeRateError = null
        } else if (item.tasaPromedio <= 0.0) {
            exchangeRateError = "No se ha definido una tasa de cambio vigente para la moneda seleccionada a la fecha de confirmación."
        } else {
            exchangeRateError = null
        }
    }

    fun syncBcuRate() {
        val originDateStr = fechaConfirmacion
        val cal = Calendar.getInstance()
        try {
            val date = dateFormat.parse(originDateStr)
            if (date != null) cal.time = date
        } catch (_: Exception) {}

        // Subtract 1 day
        cal.add(Calendar.DAY_OF_MONTH, -1)
        // Roll back if Saturday (7) or Sunday (1)
        while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        val syncDate = dateFormat.format(cal.time)

        isLoadingExchangeRate = true
        exchangeRateError = null
        viewModelScope.launch {
            repository.syncBcuRate(syncDate).onSuccess { response ->
                if (response.exitoso || response.estado == "Exitoso" || response.estado == "Parcial" || response.estado == "SinCambios") {
                    // Refetch canonical rate from catalog for fechaConfirmacion
                    repository.getTasaCambios(fechaConfirmacion).onSuccess { tasas ->
                        cachedCatalogs = cachedCatalogs?.copy(tasaCambios = tasas)
                        val currentMonedaId = selectedMoneda?.id
                        val matched = tasas.find { it.id == currentMonedaId }
                        if (matched != null && matched.tasaPromedio > 0.0) {
                            selectedMoneda = matched
                            exchangeRateError = null
                        } else {
                            exchangeRateError = response.error ?: "No fue posible obtener una tasa BCU para la fecha requerida."
                        }
                    }
                } else {
                    exchangeRateError = response.error ?: "No fue posible obtener una tasa BCU para la fecha requerida."
                }
            }.onFailure { err ->
                exchangeRateError = err.message ?: "Error al sincronizar con BCU."
            }
            isLoadingExchangeRate = false
        }
    }

    fun loadPuntosVenta() {
        viewModelScope.launch {
            uiState = EmissionUiState.LoadingInitial
            repository.getPuntosVenta().onSuccess { pvs ->
                val defaultPos = pvs.find { it.esPredeterminado } ?: pvs.firstOrNull()
                if (defaultPos != null) {
                    selectPOS(defaultPos)
                } else {
                    uiState = EmissionUiState.SelectPOS(pvs)
                }
            }.onFailure { handleFailure(it) }
        }
    }

    fun selectPOS(pv: PuntoVentaDto) {
        selectedPOS = pv
        selectedFiscalType = null // Invalida selección CFE al cambiar POS
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
        clearOriginDocument()
        loadCatalogsAndGoToForm(item)
    }

    private fun loadCatalogsAndGoToForm(item: CfeFiscalDocumentAvailabilityItemDto) {
        val currentPos = selectedPOS ?: return
        viewModelScope.launch {
            uiState = EmissionUiState.Processing("Cargando catálogos...")
            try {
                val empresaRes = repository.getEmpresa().getOrNull()
                val empresaMonedaId = empresaRes?.moneda?.id ?: 50
                baseCurrencyId = empresaMonedaId

                tasaCambioConfig = repository.getTasaCambioConfig().getOrNull()

                val data = CatalogData(
                    tasaCambios = repository.getTasaCambios(fechaConfirmacion).getOrThrow(),
                    almacenes = repository.getAlmacenes().getOrThrow(),
                    vencimientos = repository.getVencimientos().getOrNull() ?: emptyList(),
                    listasPrecio = repository.getListasPrecio().getOrNull() ?: emptyList(),
                    indicadoresC4 = repository.getIndicadoresFacturacion(
                        item.cfeCode, item.puntoVentaId, item.serie, fechaConfirmacion
                    ).getOrNull() ?: emptyList()
                )
                cachedCatalogs = data

                val defaultMoneda = data.tasaCambios.find { it.id == baseCurrencyId }
                    ?: data.tasaCambios.find { it.codigo == "UYU" }
                    ?: data.tasaCambios.firstOrNull()
                if (defaultMoneda != null) {
                    selectedMoneda = defaultMoneda
                }
                if (selectedAlmacen == null) selectedAlmacen = data.almacenes.firstOrNull()

                uiState = EmissionUiState.FillForm(item, currentPos, data)
            } catch (e: Exception) {
                handleFailure(e)
            }
        }
    }

    fun initClientSearch() {
        searchClients("")
    }

    fun initProductSearch() {
        searchProducts("")
    }

    fun searchClients(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotEmpty()) {
                delay(300)
            }
            isSearching = true
            repository.getClientes(query.ifBlank { null }).onSuccess { clients ->
                val cfeCode = selectedFiscalType?.cfeCode
                clientSearchResults = if (cfeCode == 111 || cfeCode == 112 || cfeCode == 113) {
                    clients.filter { isClientCompatibleWithCfe(it.tipoDocumentoIdentificacion, 111) }
                } else {
                    clients
                }
            }
            isSearching = false
        }
    }

    fun searchProducts(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotEmpty()) {
                delay(300)
            }
            isSearching = true
            repository.getProductos(query.ifBlank { null }).onSuccess {
                productSearchResults = it
            }
            isSearching = false
        }
    }

    fun startLineConfiguration(producto: ProductoDto) {
        val type = selectedFiscalType ?: return
        val pos = selectedPOS ?: return

        editingLineIndex = null
        productBeingConfigured = producto
        dialogQuantityText = "1"
        dialogUnitPriceText = if (isBaseCurrency()) (producto.precio ?: 0.0).toString() else "0"
        lineDialogError = null
        isResolvingC4 = true
        isConfiguringLine = true

        viewModelScope.launch {
            repository.getIndicadorSugerido(
                cfeCode = type.cfeCode,
                tasaIva = producto.tasaIva ?: 0.0,
                currentValue = null,
                puntoVentaId = pos.id,
                seriePreferida = type.serie,
                fechaEmision = fechaConfirmacion
            ).onSuccess {
                lineConfigurationSugerido = it
            }.onFailure {
                lineConfigurationSugerido = null
            }
            isResolvingC4 = false
        }
    }

    fun openLineEditDialog(index: Int) {
        if (index in lineas.indices) {
            val item = lineas[index]
            editingLineIndex = index
            productBeingConfigured = item.producto
            dialogQuantityText = item.cantidad.toString()
            dialogUnitPriceText = item.precioUnitario.toString()
            lineDialogError = null
            isConfiguringLine = true
        }
    }

    fun confirmLineConfiguration(cantidad: Double, precio: Double, indicadorC4: Int?, sugerido: Int? = null, label: String? = null) {
        val producto = productBeingConfigured ?: return
        val editIdx = editingLineIndex
        if (editIdx != null && editIdx in lineas.indices) {
            val currentItem = lineas[editIdx]
            lineas[editIdx] = currentItem.copy(
                cantidad = cantidad,
                precioUnitario = precio,
                indicadorFacturacionC4 = indicadorC4 ?: currentItem.indicadorFacturacionC4
            )
        } else {
            lineas.add(LineaForm(
                producto = producto,
                cantidad = cantidad,
                precioUnitario = precio,
                indicadorFacturacionC4 = indicadorC4,
                indicadorFacturacionC4Sugerido = sugerido,
                indicadorFacturacionC4SugeridoLabel = label
            ))
        }
        cancelLineConfiguration()
    }

    fun confirmLineEdit(quantityStr: String, priceStr: String): Boolean {
        val qty = quantityStr.replace(',', '.').toDoubleOrNull()
        if (qty == null || qty <= 0) {
            lineDialogError = "Cantidad debe ser mayor a 0"
            return false
        }

        val price = priceStr.replace(',', '.').toDoubleOrNull()
        if (price == null || price < 0) {
            lineDialogError = "Precio debe ser mayor o igual a 0"
            return false
        }

        val editIdx = editingLineIndex
        if (editIdx != null && editIdx in lineas.indices) {
            val currentItem = lineas[editIdx]
            lineas[editIdx] = currentItem.copy(
                cantidad = qty,
                precioUnitario = price
            )
        }

        cancelLineConfiguration()
        return true
    }

    fun cancelLineConfiguration() {
        productBeingConfigured = null
        editingLineIndex = null
        isConfiguringLine = false
        lineConfigurationSugerido = null
        isResolvingC4 = false
        lineDialogError = null
    }

    fun removeLinea(index: Int) {
        if (index in lineas.indices) {
            lineas.removeAt(index)
        }
    }

    fun proceedToEmission() {
        val type = selectedFiscalType
        val pos = selectedPOS
        val cliente = selectedCliente

        if (type == null || pos == null || type.serie.isNullOrBlank()) {
            uiState = EmissionUiState.Error(AppError.Validation("Error de ruta fiscal: Serie o Punto de Venta no válidos para el borrador electrónico.\n"))
            return
        }

        if (isOriginRequired() && idDocumentoOrigen == null) {
            uiState = EmissionUiState.Error(AppError.Validation("Debe seleccionar un documento de origen"))
            return
        }

        if (type.cfeCode == 111 && !isClientCompatibleWithCfe(cliente?.tipoDocumentoIdentificacion, 111)) {
            uiState = EmissionUiState.Error(AppError.Validation("e-Factura requiere un cliente con RUT/RUC."))
            return
        }

        if (fechaEmision.isNotBlank() && fechaConfirmacion.isNotBlank() && fechaEmision > fechaConfirmacion) {
            uiState = EmissionUiState.Error(AppError.Validation("La fecha de emisión no puede ser posterior a la fecha de confirmación."))
            return
        }

        if (isLoadingExchangeRate) {
            uiState = EmissionUiState.Error(AppError.Validation("Cargando tasa de cambio..."))
            return
        }

        if (!isBaseCurrency() && (selectedMoneda?.tasaPromedio ?: 0.0) <= 0.0) {
            uiState = EmissionUiState.Error(AppError.Validation(
                exchangeRateError ?: "No se ha definido una tasa de cambio vigente para la moneda seleccionada a la fecha de confirmación."
            ))
            return
        }

        viewModelScope.launch {
            // 1. Precheck CAE
            uiState = EmissionUiState.Processing("Verificando salud de CAE...")
            val precheck = repository.caePrecheck(pos.id, type.cfeCode, type.serie, fechaConfirmacion).getOrNull()
            if (precheck != null && !precheck.hasValidCae) {
                uiState = EmissionUiState.Error(AppError.Validation(precheck.message ?: "CAE no válido o vencido"))
                return@launch
            }

            // 2. Create ERP
            uiState = EmissionUiState.Processing("Creando documento ERP...")
            val result = if (isVenta(type.cfeCode)) {
                createFacturaERP(type, pos)
            } else {
                createDevolucionERP(type.cfeCode, type, pos)
            }

            result.onSuccess { documentoId ->
                validateAndEmit(documentoId, type, pos)
            }.onFailure { handleFailure(it) }
        }
    }

    private suspend fun createFacturaERP(type: CfeFiscalDocumentAvailabilityItemDto, pos: PuntoVentaDto): Result<Long> {
        val isBase = isBaseCurrency()
        val tasaCambio = if (isBase) 1.0 else selectedMoneda!!.tasaPromedio

        val mappedLineas = mapLineas(isBase)

        val importeBase = mappedLineas.sumOf { it.importeBase }
        val iva = mappedLineas.sumOf { it.iva }

        val importeOriginal = if (isBase) 0.0 else mappedLineas.sumOf { it.importeOriginal }
        val ivaOriginal = if (isBase) 0.0 else mappedLineas.sumOf { it.ivaOriginal }
        val importeTotalOriginal = if (isBase) 0.0 else mappedLineas.sumOf { it.importeOriginalConIva }

        val finalNota = InvoiceNoteSanitizer.sanitizeInvoiceNote(notas)

        val request = FacturaCreateDto(
            fechaEmision = fechaEmision,
            fechaConfirmacion = fechaConfirmacion,
            idMoneda = selectedMoneda?.id ?: 0,
            tasaCambio = tasaCambio,
            importeBase = importeBase,
            iva = iva,
            importeTotalBase = importeBase + iva,
            importeOriginal = importeOriginal,
            ivaOriginal = ivaOriginal,
            importeTotalOriginal = importeTotalOriginal,
            idAlmacen = selectedAlmacen?.id ?: 0,
            idCliente = selectedCliente?.id ?: 0,
            documentoProductos = mappedLineas,
            nota = finalNota,
            preciosIncluyenIva = preciosIncluyenIva,
            esElectronico = true,
            idVencimiento = selectedVencimiento?.id,
            idListaPrecio = selectedListaPrecio?.id,
            cfeCodeIntent = type.cfeCode,
            puntoVentaFiscalIntentId = pos.id,
            serieFiscalPreferidaIntent = type.serie,
            condicionPagoComercial = selectedCondicionPago.apiValue
        )
        return repository.createFacturaElectronicDraft(request)
    }

    private suspend fun createDevolucionERP(cfeCode: Int, type: CfeFiscalDocumentAvailabilityItemDto, pos: PuntoVentaDto): Result<Long> {
        if (idDocumentoOrigen == null) return Result.failure(Exception("Debe seleccionar un documento de origen"))

        val isBase = isBaseCurrency()
        val tasaCambio = if (isBase) 1.0 else selectedMoneda!!.tasaPromedio

        val mappedLineas = mapDevolucionLineas(isBase)
        val importeBase = mappedLineas.sumOf { it.importeBase }
        val iva = mappedLineas.sumOf { it.iva }

        val importeOriginal = if (isBase) 0.0 else mappedLineas.sumOf { it.importeOriginal }
        val ivaOriginal = if (isBase) 0.0 else mappedLineas.sumOf { it.ivaOriginal }
        val importeTotalOriginal = if (isBase) 0.0 else mappedLineas.sumOf { it.importeOriginalConIva }

        val naturaleza = when (cfeCode) {
            102, 112 -> 1
            103, 113 -> 2
            else -> 1
        }

        val finalNota = InvoiceNoteSanitizer.sanitizeInvoiceNote(notas)

        val request = DevolucionCreateDto(
            fechaEmision = fechaEmision,
            fechaConfirmacion = fechaConfirmacion,
            idMoneda = selectedMoneda?.id ?: 0,
            tasaCambio = tasaCambio,
            importeBase = importeBase,
            iva = iva,
            importeTotalBase = importeBase + iva,
            importeOriginal = importeOriginal,
            ivaOriginal = ivaOriginal,
            importeTotalOriginal = importeTotalOriginal,
            idAlmacen = selectedAlmacen?.id ?: 0,
            idCliente = selectedCliente?.id ?: 0,
            documentoProductos = mappedLineas,
            cantidadPrecio = true,
            nota = finalNota,
            preciosIncluyenIva = preciosIncluyenIva,
            esElectronico = true,
            tipoDevolucion = 2,
            naturalezaNota = naturaleza,
            idDocumentoOrigen = idDocumentoOrigen!!,
            idVencimiento = selectedVencimiento?.id,
            idListaPrecio = selectedListaPrecio?.id,
            cfeCodeIntent = type.cfeCode,
            puntoVentaFiscalIntentId = pos.id,
            serieFiscalPreferidaIntent = type.serie,
            condicionPagoComercial = selectedCondicionPago.apiValue
        )
        return repository.createDevolucion(request)
    }

    private fun mapLineas(isBaseCurrency: Boolean): List<FacturaLineaRequest> {
        val tasaCambio = if (isBaseCurrency) 1.0 else (selectedMoneda?.tasaPromedio ?: 1.0)

        return lineas.map {
            val price = it.precioUnitario // Typed by user in Document Currency
            val qty = it.cantidad
            val taxRate = it.producto.tasaIva ?: 0.0

            if (isBaseCurrency) {
                val lineTotalInput = price * qty
                val importeBase: Double
                val iva: Double

                if (preciosIncluyenIva && taxRate > 0) {
                    importeBase = lineTotalInput / (1 + taxRate)
                    iva = lineTotalInput - importeBase
                } else {
                    importeBase = lineTotalInput
                    iva = importeBase * taxRate
                }
                val totalConIva = importeBase + iva
                val precioBaseCalculated = if (preciosIncluyenIva && taxRate > 0) price / (1 + taxRate) else price

                FacturaLineaRequest(
                    idProducto = it.producto.id,
                    cantidad = qty,
                    precioBase = precioBaseCalculated,
                    importeBase = importeBase,
                    iva = iva,
                    descuento = it.descuento,
                    ivaOriginal = 0.0,
                    descuentoOriginal = 0.0,
                    precioBaseConIva = if (preciosIncluyenIva || taxRate == 0.0) price else price * (1 + taxRate),
                    importeBaseConIva = totalConIva,
                    precioOriginal = 0.0,
                    importeOriginal = 0.0,
                    precioOriginalConIva = 0.0,
                    importeOriginalConIva = 0.0,
                    indicadorFacturacionC4 = it.indicadorFacturacionC4
                )
            } else {
                // Foreign Currency (e.g. USD) -> price is PRECIO ORIGINAL
                val lineTotalOriginalInput = price * qty
                val importeOriginal: Double
                val ivaOriginal: Double

                if (preciosIncluyenIva && taxRate > 0) {
                    importeOriginal = lineTotalOriginalInput / (1 + taxRate)
                    ivaOriginal = lineTotalOriginalInput - importeOriginal
                } else {
                    importeOriginal = lineTotalOriginalInput
                    ivaOriginal = importeOriginal * taxRate
                }
                val totalOriginalConIva = importeOriginal + ivaOriginal
                val precioOriginalCalculated = if (preciosIncluyenIva && taxRate > 0) price / (1 + taxRate) else price

                // Derived Base Amounts = Original * tasaCambio
                val precioBase = precioOriginalCalculated * tasaCambio
                val importeBase = importeOriginal * tasaCambio
                val ivaBase = ivaOriginal * tasaCambio
                val totalBaseConIva = totalOriginalConIva * tasaCambio

                FacturaLineaRequest(
                    idProducto = it.producto.id,
                    cantidad = qty,
                    precioBase = precioBase,
                    importeBase = importeBase,
                    iva = ivaBase,
                    descuento = it.descuento * tasaCambio,
                    ivaOriginal = ivaOriginal,
                    descuentoOriginal = it.descuento,
                    precioBaseConIva = (if (preciosIncluyenIva || taxRate == 0.0) price else price * (1 + taxRate)) * tasaCambio,
                    importeBaseConIva = totalBaseConIva,
                    precioOriginal = precioOriginalCalculated,
                    importeOriginal = importeOriginal,
                    precioOriginalConIva = if (preciosIncluyenIva || taxRate == 0.0) price else price * (1 + taxRate),
                    importeOriginalConIva = totalOriginalConIva,
                    indicadorFacturacionC4 = it.indicadorFacturacionC4
                )
            }
        }
    }

    private fun mapDevolucionLineas(isBaseCurrency: Boolean): List<DevolucionLineaRequest> {
        val tasaCambio = if (isBaseCurrency) 1.0 else (selectedMoneda?.tasaPromedio ?: 1.0)

        return lineas.map {
            val price = it.precioUnitario // Typed by user in Document Currency
            val qty = it.cantidad
            val taxRate = it.producto.tasaIva ?: 0.0

            if (isBaseCurrency) {
                val lineTotalInput = price * qty
                val importeBase: Double
                val iva: Double

                if (preciosIncluyenIva && taxRate > 0) {
                    importeBase = lineTotalInput / (1 + taxRate)
                    iva = lineTotalInput - importeBase
                } else {
                    importeBase = lineTotalInput
                    iva = importeBase * taxRate
                }
                val totalConIva = importeBase + iva
                val precioBaseCalculated = if (preciosIncluyenIva && taxRate > 0) price / (1 + taxRate) else price

                DevolucionLineaRequest(
                    idProducto = it.producto.id,
                    cantidad = qty,
                    devuelto = qty,
                    precioBase = precioBaseCalculated,
                    importeBase = importeBase,
                    iva = iva,
                    descuento = it.descuento,
                    ivaOriginal = 0.0,
                    descuentoOriginal = 0.0,
                    precioBaseConIva = if (preciosIncluyenIva || taxRate == 0.0) price else price * (1 + taxRate),
                    importeBaseConIva = totalConIva,
                    precioOriginal = 0.0,
                    importeOriginal = 0.0,
                    precioOriginalConIva = 0.0,
                    importeOriginalConIva = 0.0,
                    idSkuVariante = it.idSkuVariante
                )
            } else {
                // Foreign Currency (e.g. USD) -> price is PRECIO ORIGINAL
                val lineTotalOriginalInput = price * qty
                val importeOriginal: Double
                val ivaOriginal: Double

                if (preciosIncluyenIva && taxRate > 0) {
                    importeOriginal = lineTotalOriginalInput / (1 + taxRate)
                    ivaOriginal = lineTotalOriginalInput - importeOriginal
                } else {
                    importeOriginal = lineTotalOriginalInput
                    ivaOriginal = importeOriginal * taxRate
                }
                val totalOriginalConIva = importeOriginal + ivaOriginal
                val precioOriginalCalculated = if (preciosIncluyenIva && taxRate > 0) price / (1 + taxRate) else price

                // Derived Base Amounts = Original * tasaCambio
                val precioBase = precioOriginalCalculated * tasaCambio
                val importeBase = importeOriginal * tasaCambio
                val ivaBase = ivaOriginal * tasaCambio
                val totalBaseConIva = totalOriginalConIva * tasaCambio

                DevolucionLineaRequest(
                    idProducto = it.producto.id,
                    cantidad = qty,
                    devuelto = qty,
                    precioBase = precioBase,
                    importeBase = importeBase,
                    iva = ivaBase,
                    descuento = it.descuento * tasaCambio,
                    ivaOriginal = ivaOriginal,
                    descuentoOriginal = it.descuento,
                    precioBaseConIva = (if (preciosIncluyenIva || taxRate == 0.0) price else price * (1 + taxRate)) * tasaCambio,
                    importeBaseConIva = totalBaseConIva,
                    precioOriginal = precioOriginalCalculated,
                    importeOriginal = importeOriginal,
                    precioOriginalConIva = if (preciosIncluyenIva || taxRate == 0.0) price else price * (1 + taxRate),
                    importeOriginalConIva = totalOriginalConIva,
                    idSkuVariante = it.idSkuVariante
                )
            }
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

            uiState = EmissionUiState.Processing("Procesando emisión fiscal...")
            val emitReq = CfeEmitRequest(
                puntoVentaId = pos.id,
                seriePreferida = item.serie,
                cfeCode = item.cfeCode,
                ncAdjustmentMode = null
            )

            repository.emitCfe(documentoId, emitReq).onSuccess { response ->
                startPolling(documentoId, response.statusUrl)
            }.onFailure { handleFailure(it) }
        }.onFailure { handleFailure(it) }
    }

    private fun startPolling(documentoId: Long, statusUrl: String?) {
        viewModelScope.launch {
            uiState = EmissionUiState.Processing("Consultando estado final...")
            repository.getCfeStatus(documentoId, statusUrl).onSuccess { status ->
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

    fun resetToTypeSelection() {
        selectedFiscalType = null
        resetForm()
        val pos = selectedPOS
        if (pos != null) {
            selectPOS(pos)
        } else {
            resetToStart()
        }
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
        selectedOriginCfe = null
        selectedOriginDoc = null
        clientSearchResults = emptyList()
        productSearchResults = emptyList()
        originCfeSearchResults = emptyList()
        selectedMoneda = null
        selectedCondicionPago = CondicionPagoComercial.CONTADO
        cancelLineConfiguration()
    }
}

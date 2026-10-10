package com.authvex.balaxysefactura.ui.screens.collection.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.core.repository.CollectionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class PendingInvoiceItem(
    val factura: CollectionInvoiceDto,
    val pendingBase: Double,
    val pendingOriginal: Double,
    var montoCobrarText: String = "0.0",
    var isSelected: Boolean = false
) {
    fun getPendingDisplay(currencyCode: String): Double {
        return if (currencyCode != "UYU" && pendingOriginal > 0) pendingOriginal else pendingBase
    }
}

sealed class CollectionFormUiState {
    object Idle : CollectionFormUiState()
    object Loading : CollectionFormUiState()
    data class Success(val collectionId: Long, val message: String) : CollectionFormUiState()
    data class Error(val message: String) : CollectionFormUiState()
}

class CollectionFormViewModel(
    private val collectionRepository: CollectionRepository,
    private val cfeRepository: CfeRepository,
    val facturaIdInicial: Long? = null
) : ViewModel() {

    var uiState by mutableStateOf<CollectionFormUiState>(CollectionFormUiState.Idle)
        private set

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    var fechaEmision by mutableStateOf(getTodayDate())
    var fechaConfirmacion by mutableStateOf(getTodayDate())
    var numeroReferencia by mutableStateOf("")
    var nota by mutableStateOf("")

    var companyBaseCurrencyId by mutableStateOf<Int?>(null)
    var isClientLocked by mutableStateOf(false)

    var selectedCliente by mutableStateOf<ClienteDto?>(null)
    var selectedCuentaBanco by mutableStateOf<CuentaBancoCatalogDto?>(null)
    var selectedFormaPago by mutableStateOf<CatalogoItemDto?>(null)
    var selectedMoneda by mutableStateOf<TasaCambioSimpleDto?>(null)
    var tasaCambio by mutableStateOf(1.0)

    val pendingInvoices = mutableStateListOf<PendingInvoiceItem>()

    // Catalogs
    var clientesList by mutableStateOf<List<ClienteDto>>(emptyList())
    var cuentasBancoList by mutableStateOf<List<CuentaBancoCatalogDto>>(emptyList())
    var allowedFormasPagoList by mutableStateOf<List<CatalogoItemDto>>(emptyList())
    var tasasCambioList by mutableStateOf<List<TasaCambioSimpleDto>>(emptyList())

    var isSubmitting by mutableStateOf(false)

    private var clientSearchJob: Job? = null

    init {
        loadInitialData()
    }

    private fun getTodayDate(): String = dateFormat.format(Date())

    private fun loadInitialData() {
        viewModelScope.launch {
            cfeRepository.getEmpresa().onSuccess { empresa ->
                companyBaseCurrencyId = empresa.moneda?.id
            }

            cfeRepository.getClientes().onSuccess { clientesList = it }

            cfeRepository.getTasaCambios(fechaConfirmacion).onSuccess { tasas ->
                tasasCambioList = tasas
                resolveSelectedMoneda(tasas)
            }

            collectionRepository.getCuentasBanco().onSuccess { cuentas ->
                cuentasBancoList = cuentas
                syncCuentasBancoForSelectedMoneda()
            }

            if (facturaIdInicial != null && facturaIdInicial > 0) {
                isClientLocked = true
                loadInitialFactura(facturaIdInicial)
            }
        }
    }

    private suspend fun loadInitialFactura(facturaId: Long) {
        cfeRepository.getFacturaById(facturaId).onSuccess { erpFactura ->
            selectedCliente = erpFactura.cliente

            val docCurrency = erpFactura.moneda
            if (docCurrency != null) {
                val matched = tasasCambioList.find { it.id == docCurrency.id }
                if (matched != null) {
                    onMonedaSelected(matched)
                }
            }

            val collectionInvoice = CollectionInvoiceDto(
                id = erpFactura.id,
                folio = erpFactura.folio,
                numero = erpFactura.numero,
                fechaEmision = erpFactura.fechaEmision,
                fechaConfirmacion = erpFactura.fechaConfirmacion,
                esElectronico = erpFactura.esElectronico,
                estado = erpFactura.estado,
                moneda = erpFactura.moneda,
                tasaCambio = erpFactura.tasaCambio,
                importeTotalBase = erpFactura.importeTotalBase,
                importeTotalOriginal = erpFactura.importeTotalOriginal,
                cliente = erpFactura.cliente
            )

            val pendingItem = createPendingInvoiceItem(collectionInvoice)
            if (pendingItem != null) {
                val initAmount = pendingItem.getPendingDisplay(selectedMoneda?.codigo ?: "UYU")
                pendingItem.montoCobrarText = String.format(Locale.US, "%.2f", initAmount)
                pendingItem.isSelected = true
                pendingInvoices.clear()
                pendingInvoices.add(pendingItem)
            }
        }
    }

    fun onClientSelected(cliente: ClienteDto) {
        if (isClientLocked) return
        selectedCliente = cliente
        loadInvoicesForSelectedClient(cliente.id.toLong())
    }

    fun onClientQueryChanged(query: String) {
        clientSearchJob?.cancel()
        clientSearchJob = viewModelScope.launch {
            delay(300)
            cfeRepository.getClientes(query).onSuccess { clientesList = it }
        }
    }

    private fun loadInvoicesForSelectedClient(clienteId: Long) {
        viewModelScope.launch {
            collectionRepository.getCollectableInvoices(clienteId).onSuccess { response ->
                val items = response.items.mapNotNull { createPendingInvoiceItem(it) }
                pendingInvoices.clear()
                pendingInvoices.addAll(items)
            }
        }
    }

    private fun createPendingInvoiceItem(factura: CollectionInvoiceDto): PendingInvoiceItem? {
        val (pendingBase, pendingOrig) = factura.calculatePendingBalances()

        if (pendingBase <= 0.00001 && pendingOrig <= 0.00001) {
            return null
        }

        return PendingInvoiceItem(
            factura = factura,
            pendingBase = pendingBase,
            pendingOriginal = pendingOrig
        )
    }

    fun getFilteredCuentasBanco(): List<CuentaBancoCatalogDto> {
        val selMonedaId = selectedMoneda?.id?.toLong() ?: return emptyList()
        return cuentasBancoList.filter {
            it.moneda?.id == selectedMoneda?.id || it.idMoneda == selMonedaId
        }
    }

    private fun syncCuentasBancoForSelectedMoneda() {
        val validCuentas = getFilteredCuentasBanco()
        if (selectedCuentaBanco != null && validCuentas.none { it.id == selectedCuentaBanco!!.id }) {
            selectedCuentaBanco = null
            selectedFormaPago = null
            allowedFormasPagoList = emptyList()
        }
        if (selectedCuentaBanco == null && validCuentas.isNotEmpty()) {
            onCuentaBancoSelected(validCuentas.first())
        }
    }

    fun onCuentaBancoSelected(cuenta: CuentaBancoCatalogDto) {
        selectedCuentaBanco = cuenta
        selectedFormaPago = null
        allowedFormasPagoList = emptyList()

        viewModelScope.launch {
            collectionRepository.getAllowedFormasPago(cuenta.id).onSuccess { allowed ->
                allowedFormasPagoList = allowed
                if (allowed.isNotEmpty()) {
                    selectedFormaPago = allowed.first()
                }
            }.onFailure {
                allowedFormasPagoList = emptyList()
            }
        }
    }

    fun onFechaConfirmacionChanged(newDate: String) {
        fechaConfirmacion = newDate
        viewModelScope.launch {
            cfeRepository.getTasaCambios(newDate).onSuccess { tasas ->
                tasasCambioList = tasas
                val currentId = selectedMoneda?.id
                val matched = tasas.find { it.id == currentId } ?: tasas.find { it.id == companyBaseCurrencyId } ?: tasas.firstOrNull()
                if (matched != null) {
                    onMonedaSelected(matched)
                }
            }
        }
    }

    fun onMonedaSelected(moneda: TasaCambioSimpleDto) {
        selectedMoneda = moneda
        val isBase = companyBaseCurrencyId != null && moneda.id == companyBaseCurrencyId
        tasaCambio = if (isBase) 1.0 else (if (moneda.tasaPromedio > 0) moneda.tasaPromedio else 1.0)
        syncCuentasBancoForSelectedMoneda()
    }

    private fun resolveSelectedMoneda(tasas: List<TasaCambioSimpleDto>) {
        val baseId = companyBaseCurrencyId
        val matched = if (baseId != null) tasas.find { it.id == baseId } else null
        val selected = matched ?: tasas.firstOrNull()
        if (selected != null) {
            onMonedaSelected(selected)
        }
    }

    fun calculateTotalOperacion(): Double {
        return pendingInvoices.filter { it.isSelected }.sumOf { item ->
            item.montoCobrarText.replace(',', '.').toDoubleOrNull() ?: 0.0
        }
    }

    fun saveCollection(andConfirm: Boolean) {
        if (isSubmitting) return

        val cliente = selectedCliente
        if (cliente == null) {
            uiState = CollectionFormUiState.Error("Debe seleccionar un cliente")
            return
        }

        val moneda = selectedMoneda
        if (moneda == null) {
            uiState = CollectionFormUiState.Error("Debe seleccionar una moneda")
            return
        }

        val cuenta = selectedCuentaBanco
        if (cuenta == null) {
            uiState = CollectionFormUiState.Error("Debe seleccionar una cuenta bancaria / caja")
            return
        }

        val formaPago = selectedFormaPago
        if (formaPago == null) {
            uiState = CollectionFormUiState.Error("Debe seleccionar una forma de pago")
            return
        }

        val selectedInvoices = pendingInvoices.filter { it.isSelected && (it.montoCobrarText.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0 }
        if (selectedInvoices.isEmpty()) {
            uiState = CollectionFormUiState.Error("Debe seleccionar al menos una factura con importe a cobrar mayor a cero")
            return
        }

        if (fechaEmision > fechaConfirmacion) {
            uiState = CollectionFormUiState.Error("La fecha de emisión no puede ser posterior a la fecha de confirmación")
            return
        }

        isSubmitting = true
        uiState = CollectionFormUiState.Loading

        viewModelScope.launch {
            try {
                val isBaseCurrency = companyBaseCurrencyId != null && moneda.id == companyBaseCurrencyId
                val rate = if (isBaseCurrency) 1.0 else (if (tasaCambio > 0) tasaCambio else 1.0)

                val totalOperacion = calculateTotalOperacion()
                val totalBase = if (isBaseCurrency) totalOperacion else totalOperacion * rate
                val totalOrig = if (isBaseCurrency) 0.0 else totalOperacion

                val facturaCobrosRequests = selectedInvoices.map { item ->
                    val amountOp = item.montoCobrarText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val amountBase = if (isBaseCurrency) amountOp else amountOp * rate
                    val amountOrig = if (isBaseCurrency) 0.0 else amountOp

                    FacturaCobroCreateRequest(
                        idFactura = item.factura.id,
                        montoActualBase = amountBase,
                        montoBase = amountBase,
                        montoActualOriginal = amountOrig,
                        montoOriginal = amountOrig,
                        montoOperacion = amountOp
                    )
                }

                val createDto = CobroCreateDto(
                    fechaEmision = fechaEmision,
                    fechaConfirmacion = fechaConfirmacion,
                    numeroReferencia = numeroReferencia.takeIf { it.isNotBlank() },
                    nota = nota.takeIf { it.isNotBlank() },
                    tipoDocumentoFinanza = 1, // ContraFactura
                    idCuentaBanco = cuenta.id,
                    idFormaPago = formaPago.id.toLong(),
                    idMoneda = moneda.id.toLong(),
                    tasaCambio = rate,
                    importeBase = totalBase,
                    montoTotalBase = totalBase,
                    importeOriginal = totalOrig,
                    montoTotalOriginal = totalOrig,
                    idCliente = cliente.id.toLong(),
                    facturaCobros = facturaCobrosRequests
                )

                val postResult = collectionRepository.createCobro(createDto)
                postResult.onSuccess { createdId ->
                    if (andConfirm) {
                        val confirmResult = collectionRepository.confirmCobro(createdId)
                        confirmResult.onSuccess {
                            uiState = CollectionFormUiState.Success(createdId, "Cobro N° $createdId guardado y confirmado correctamente")
                        }.onFailure { err ->
                            uiState = CollectionFormUiState.Success(createdId, "El cobro se guardó correctamente (N° $createdId), pero no se pudo confirmar: ${err.message}")
                        }
                    } else {
                        uiState = CollectionFormUiState.Success(createdId, "Cobro N° $createdId guardado correctamente")
                    }
                }.onFailure { throwable ->
                    val appError = ErrorMapper.fromThrowable(throwable)
                    uiState = CollectionFormUiState.Error(appError.getDisplayMessage())
                }
            } finally {
                isSubmitting = false
            }
        }
    }

    fun resetState() {
        uiState = CollectionFormUiState.Idle
    }
}

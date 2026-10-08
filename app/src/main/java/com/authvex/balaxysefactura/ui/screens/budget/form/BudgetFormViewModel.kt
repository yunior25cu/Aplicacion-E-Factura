package com.authvex.balaxysefactura.ui.screens.budget.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.BudgetRepository
import com.authvex.balaxysefactura.core.repository.CfeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class BudgetFormLineItem(
    val producto: ProductoDto,
    var cantidad: Double = 1.0,
    var precioUnitario: Double = producto.precio ?: 0.0,
    var descuento: Double = 0.0
)

sealed class BudgetFormUiState {
    object Idle : BudgetFormUiState()
    object Loading : BudgetFormUiState()
    data class Success(val budgetId: Long) : BudgetFormUiState()
    data class Error(val message: String) : BudgetFormUiState()
}

class BudgetFormViewModel(
    private val budgetRepository: BudgetRepository,
    private val cfeRepository: CfeRepository
) : ViewModel() {

    var uiState by mutableStateOf<BudgetFormUiState>(BudgetFormUiState.Idle)
        private set

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    var fechaEmision by mutableStateOf(getTodayDate())
    var fechaConfirmacion by mutableStateOf(getTodayDate())
    var fechaVencimiento by mutableStateOf(getFutureDate(30))

    var selectedCliente by mutableStateOf<ClienteDto?>(null)
    var selectedAlmacen by mutableStateOf<CatalogoItemDto?>(null)
    var selectedMoneda by mutableStateOf<CatalogoItemDto?>(null)
    var tasaCambio by mutableStateOf(1.0)
    var preciosIncluyenIva by mutableStateOf(true)

    var numeroReferencia by mutableStateOf("")
    var nota by mutableStateOf("")
    var terminoCondiciones by mutableStateOf("")

    var lineItems = mutableStateOf<List<BudgetFormLineItem>>(emptyList())

    // Catalogs
    var clientesList by mutableStateOf<List<ClienteDto>>(emptyList())
    var almacenesList by mutableStateOf<List<CatalogoItemDto>>(emptyList())
    var monedasList by mutableStateOf<List<CatalogoItemDto>>(emptyList())
    var productosList by mutableStateOf<List<ProductoDto>>(emptyList())

    var isCatalogsLoading by mutableStateOf(false)
    private var clientSearchJob: Job? = null
    private var productSearchJob: Job? = null

    init {
        loadInitialCatalogs()
    }

    private fun getTodayDate(): String = dateFormat.format(Date())

    private fun getFutureDate(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, days)
        return dateFormat.format(cal.time)
    }

    private fun loadInitialCatalogs() {
        viewModelScope.launch {
            isCatalogsLoading = true
            cfeRepository.getClientes().onSuccess { clientesList = it }
            cfeRepository.getAlmacenes().onSuccess {
                almacenesList = it
                if (it.isNotEmpty() && selectedAlmacen == null) {
                    selectedAlmacen = it.first()
                }
            }
            cfeRepository.getMonedas().onSuccess {
                monedasList = it
                if (it.isNotEmpty() && selectedMoneda == null) {
                    val defaultMoneda = it.find { m -> m.codigo?.contains("UYU", ignoreCase = true) == true || m.nombre.contains("Peso", ignoreCase = true) } ?: it.first()
                    selectedMoneda = defaultMoneda
                    updateExchangeRate(defaultMoneda)
                }
            }
            cfeRepository.getProductos().onSuccess { productosList = it }
            isCatalogsLoading = false
        }
    }

    fun onClientQueryChanged(query: String) {
        clientSearchJob?.cancel()
        clientSearchJob = viewModelScope.launch {
            delay(300)
            cfeRepository.getClientes(query).onSuccess { clientesList = it }
        }
    }

    fun onProductQueryChanged(query: String) {
        productSearchJob?.cancel()
        productSearchJob = viewModelScope.launch {
            delay(300)
            cfeRepository.getProductos(query).onSuccess { productosList = it }
        }
    }

    fun onFechaConfirmacionChanged(newDate: String) {
        fechaConfirmacion = newDate
        selectedMoneda?.let { updateExchangeRate(it) }
    }

    fun onMonedaChanged(moneda: CatalogoItemDto) {
        selectedMoneda = moneda
        updateExchangeRate(moneda)
    }

    private fun updateExchangeRate(moneda: CatalogoItemDto) {
        val isBaseCurrency = moneda.codigo?.contains("UYU", ignoreCase = true) == true || moneda.nombre.contains("Peso", ignoreCase = true)
        if (isBaseCurrency) {
            tasaCambio = 1.0
        } else {
            viewModelScope.launch {
                cfeRepository.getTasaCambios(fechaConfirmacion).onSuccess { tasas ->
                    val tasaItem = tasas.find { it.id == moneda.id || it.codigo.equals(moneda.codigo, ignoreCase = true) }
                    if (tasaItem != null && tasaItem.tasaPromedio > 0) {
                        tasaCambio = tasaItem.tasaPromedio
                    } else if (tasas.isNotEmpty()) {
                        tasaCambio = tasas.first().tasaPromedio
                    }
                }
            }
        }
    }

    fun addLineItem(producto: ProductoDto, cantidad: Double = 1.0) {
        val currentList = lineItems.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.producto.id == producto.id }
        if (existingIndex >= 0) {
            val item = currentList[existingIndex]
            currentList[existingIndex] = item.copy(cantidad = item.cantidad + cantidad)
        } else {
            currentList.add(BudgetFormLineItem(producto = producto, cantidad = cantidad, precioUnitario = producto.precio ?: 0.0))
        }
        lineItems.value = currentList
    }

    fun removeLineItem(index: Int) {
        if (index in lineItems.value.indices) {
            val currentList = lineItems.value.toMutableList()
            currentList.removeAt(index)
            lineItems.value = currentList
        }
    }

    fun updateLineItemQuantity(index: Int, newQuantity: Double) {
        if (index in lineItems.value.indices && newQuantity > 0) {
            val currentList = lineItems.value.toMutableList()
            currentList[index] = currentList[index].copy(cantidad = newQuantity)
            lineItems.value = currentList
        }
    }

    // Calculations
    fun calculateSubtotal(): Double {
        return lineItems.value.sumOf { item ->
            val totalLine = item.cantidad * item.precioUnitario - item.descuento
            val taxRate = item.producto.tasaIva ?: 0.22
            if (preciosIncluyenIva) {
                totalLine / (1 + taxRate)
            } else {
                totalLine
            }
        }
    }

    fun calculateIva(): Double {
        return lineItems.value.sumOf { item ->
            val totalLine = item.cantidad * item.precioUnitario - item.descuento
            val taxRate = item.producto.tasaIva ?: 0.22
            if (preciosIncluyenIva) {
                totalLine - (totalLine / (1 + taxRate))
            } else {
                totalLine * taxRate
            }
        }
    }

    fun calculateTotal(): Double {
        return calculateSubtotal() + calculateIva()
    }

    fun submitForm() {
        val cliente = selectedCliente
        if (cliente == null) {
            uiState = BudgetFormUiState.Error("Debe seleccionar un cliente")
            return
        }
        val almacen = selectedAlmacen
        if (almacen == null) {
            uiState = BudgetFormUiState.Error("Debe seleccionar un almacén")
            return
        }
        val moneda = selectedMoneda
        if (moneda == null) {
            uiState = BudgetFormUiState.Error("Debe seleccionar una moneda")
            return
        }
        if (lineItems.value.isEmpty()) {
            uiState = BudgetFormUiState.Error("Debe agregar al menos un producto al presupuesto")
            return
        }

        viewModelScope.launch {
            uiState = BudgetFormUiState.Loading

            val isBaseCurrency = moneda.codigo?.contains("UYU", ignoreCase = true) == true || moneda.nombre.contains("Peso", ignoreCase = true)
            val rate = if (isBaseCurrency) 1.0 else (if (tasaCambio > 0) tasaCambio else 1.0)

            val documentProducts = lineItems.value.map { item ->
                val taxRate = item.producto.tasaIva ?: 0.22
                val lineTotalInput = item.cantidad * item.precioUnitario - item.descuento

                val lineSubtotalDocCurrency: Double
                val lineIvaDocCurrency: Double
                val lineTotalWithIvaDocCurrency: Double

                if (preciosIncluyenIva) {
                    lineTotalWithIvaDocCurrency = lineTotalInput
                    lineSubtotalDocCurrency = lineTotalInput / (1 + taxRate)
                    lineIvaDocCurrency = lineTotalInput - lineSubtotalDocCurrency
                } else {
                    lineSubtotalDocCurrency = lineTotalInput
                    lineIvaDocCurrency = lineTotalInput * taxRate
                    lineTotalWithIvaDocCurrency = lineSubtotalDocCurrency + lineIvaDocCurrency
                }

                val precioBase = if (isBaseCurrency) item.precioUnitario else item.precioUnitario * rate
                val importeBase = if (isBaseCurrency) lineSubtotalDocCurrency else lineSubtotalDocCurrency * rate
                val ivaBase = if (isBaseCurrency) lineIvaDocCurrency else lineIvaDocCurrency * rate
                val precioBaseConIva = if (isBaseCurrency) (if (preciosIncluyenIva) item.precioUnitario else item.precioUnitario * (1 + taxRate)) else (if (preciosIncluyenIva) item.precioUnitario else item.precioUnitario * (1 + taxRate)) * rate
                val importeBaseConIva = if (isBaseCurrency) lineTotalWithIvaDocCurrency else lineTotalWithIvaDocCurrency * rate

                BudgetDocumentProductCreateDto(
                    idProducto = item.producto.id.toLong(),
                    cantidad = item.cantidad,
                    precioBase = precioBase,
                    importeBase = importeBase,
                    iva = ivaBase,
                    descuento = if (isBaseCurrency) item.descuento else item.descuento * rate,
                    ivaOriginal = if (isBaseCurrency) 0.0 else lineIvaDocCurrency,
                    descuentoOriginal = if (isBaseCurrency) 0.0 else item.descuento,
                    precioBaseConIva = precioBaseConIva,
                    importeBaseConIva = importeBaseConIva,
                    precioOriginal = if (isBaseCurrency) 0.0 else item.precioUnitario,
                    importeOriginal = if (isBaseCurrency) 0.0 else lineSubtotalDocCurrency,
                    precioOriginalConIva = if (isBaseCurrency) 0.0 else (if (preciosIncluyenIva) item.precioUnitario else item.precioUnitario * (1 + taxRate)),
                    importeOriginalConIva = if (isBaseCurrency) 0.0 else lineTotalWithIvaDocCurrency
                )
            }

            val calculatedSubtotal = calculateSubtotal()
            val calculatedIva = calculateIva()
            val calculatedTotal = calculateTotal()

            val subtotalBase = if (isBaseCurrency) calculatedSubtotal else calculatedSubtotal * rate
            val ivaBase = if (isBaseCurrency) calculatedIva else calculatedIva * rate
            val totalBase = if (isBaseCurrency) calculatedTotal else calculatedTotal * rate

            val dto = BudgetCreateDto(
                fechaEmision = fechaEmision,
                fechaConfirmacion = fechaConfirmacion,
                fechaVencimiento = fechaVencimiento,
                numeroReferencia = numeroReferencia.takeIf { it.isNotBlank() },
                nota = nota.takeIf { it.isNotBlank() },
                terminoCondiciones = terminoCondiciones.takeIf { it.isNotBlank() },
                preciosIncluyenIva = preciosIncluyenIva,
                esElectronico = false,
                idMoneda = moneda.id.toLong(),
                tasaCambio = rate,
                importeBase = subtotalBase,
                iva = ivaBase,
                descuento = 0.0,
                importeTotalBase = totalBase,
                importeOriginal = if (isBaseCurrency) 0.0 else calculatedSubtotal,
                ivaOriginal = if (isBaseCurrency) 0.0 else calculatedIva,
                descuentoOriginal = 0.0,
                importeTotalOriginal = if (isBaseCurrency) 0.0 else calculatedTotal,
                idAlmacen = almacen.id.toLong(),
                idCliente = cliente.id.toLong(),
                idCentroCosto = null,
                documentoProductos = documentProducts
            )

            val result = budgetRepository.createBudget(dto)
            result.onSuccess { createdId ->
                uiState = BudgetFormUiState.Success(createdId)
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                uiState = BudgetFormUiState.Error(appError.getDisplayMessage())
            }
        }
    }

    fun resetState() {
        uiState = BudgetFormUiState.Idle
    }
}

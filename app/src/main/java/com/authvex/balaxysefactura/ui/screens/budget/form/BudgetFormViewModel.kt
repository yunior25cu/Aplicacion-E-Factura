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

    var companyBaseCurrencyId by mutableStateOf<Int?>(null)
    var selectedCliente by mutableStateOf<ClienteDto?>(null)
    var selectedAlmacen by mutableStateOf<CatalogoItemDto?>(null)
    var selectedMoneda by mutableStateOf<TasaCambioSimpleDto?>(null)
    var tasaCambio by mutableStateOf(1.0)
    var preciosIncluyenIva by mutableStateOf(true)

    var numeroReferencia by mutableStateOf("")
    var nota by mutableStateOf("")
    var terminoCondiciones by mutableStateOf("")

    var lineItems = mutableStateOf<List<BudgetFormLineItem>>(emptyList())

    // Catalogs
    var clientesList by mutableStateOf<List<ClienteDto>>(emptyList())
    var almacenesList by mutableStateOf<List<CatalogoItemDto>>(emptyList())
    var tasasCambioList by mutableStateOf<List<TasaCambioSimpleDto>>(emptyList())
    var productosList by mutableStateOf<List<ProductoDto>>(emptyList())

    var isCatalogsLoading by mutableStateOf(false)
    private var clientSearchJob: Job? = null
    private var productSearchJob: Job? = null

    // Line Editing Dialog State
    var configuringProduct by mutableStateOf<ProductoDto?>(null)
    var editingLineIndex by mutableStateOf<Int?>(null)
    var dialogQuantityText by mutableStateOf("1.0")
    var dialogUnitPriceText by mutableStateOf("0.0")
    var lineDialogError by mutableStateOf<String?>(null)

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

            cfeRepository.getEmpresa().onSuccess { empresa ->
                companyBaseCurrencyId = empresa.moneda?.id
            }

            cfeRepository.getClientes().onSuccess { clientesList = it }
            cfeRepository.getAlmacenes().onSuccess {
                almacenesList = it
                if (it.isNotEmpty() && selectedAlmacen == null) {
                    selectedAlmacen = it.first()
                }
            }

            cfeRepository.getTasaCambios(fechaConfirmacion).onSuccess { tasas ->
                tasasCambioList = tasas
                resolveSelectedMoneda(tasas)
            }

            cfeRepository.getProductos().onSuccess { productosList = it }
            isCatalogsLoading = false
        }
    }

    private fun resolveSelectedMoneda(tasas: List<TasaCambioSimpleDto>) {
        val baseId = companyBaseCurrencyId
        val matchedMoneda = if (baseId != null) {
            tasas.find { it.id == baseId }
        } else null

        val selected = matchedMoneda ?: tasas.firstOrNull()
        selectedMoneda = selected

        if (selected != null) {
            val isBase = baseId != null && selected.id == baseId
            tasaCambio = if (isBase) 1.0 else (if (selected.tasaPromedio > 0) selected.tasaPromedio else 1.0)
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
        viewModelScope.launch {
            cfeRepository.getTasaCambios(newDate).onSuccess { tasas ->
                tasasCambioList = tasas
                val currentId = selectedMoneda?.id
                val matched = tasas.find { it.id == currentId } ?: tasas.find { it.id == companyBaseCurrencyId } ?: tasas.firstOrNull()
                selectedMoneda = matched
                if (matched != null) {
                    val isBase = companyBaseCurrencyId != null && matched.id == companyBaseCurrencyId
                    tasaCambio = if (isBase) 1.0 else (if (matched.tasaPromedio > 0) matched.tasaPromedio else 1.0)
                }
            }
        }
    }

    fun onMonedaChanged(moneda: TasaCambioSimpleDto) {
        selectedMoneda = moneda
        val isBase = companyBaseCurrencyId != null && moneda.id == companyBaseCurrencyId
        tasaCambio = if (isBase) 1.0 else (if (moneda.tasaPromedio > 0) moneda.tasaPromedio else 1.0)
    }

    // Line Configuration
    fun openLineConfiguration(producto: ProductoDto, indexToEdit: Int? = null) {
        configuringProduct = producto
        editingLineIndex = indexToEdit
        lineDialogError = null

        if (indexToEdit != null && indexToEdit in lineItems.value.indices) {
            val item = lineItems.value[indexToEdit]
            dialogQuantityText = item.cantidad.toString()
            dialogUnitPriceText = item.precioUnitario.toString()
        } else {
            dialogQuantityText = "1.0"
            dialogUnitPriceText = (producto.precio ?: 0.0).toString()
        }
    }

    fun closeLineConfiguration() {
        configuringProduct = null
        editingLineIndex = null
        lineDialogError = null
    }

    fun confirmLineConfiguration(quantityStr: String, priceStr: String): Boolean {
        val qty = quantityStr.replace(',', '.').toDoubleOrNull()
        if (qty == null || qty <= 0) {
            lineDialogError = "Cantidad debe ser un número válido mayor a 0"
            return false
        }

        val price = priceStr.replace(',', '.').toDoubleOrNull()
        if (price == null || price < 0) {
            lineDialogError = "Precio debe ser un número válido mayor o igual a 0"
            return false
        }

        val prod = configuringProduct ?: return false
        val currentList = lineItems.value.toMutableList()
        val index = editingLineIndex

        if (index != null && index in currentList.indices) {
            currentList[index] = currentList[index].copy(
                producto = prod,
                cantidad = qty,
                precioUnitario = price
            )
        } else {
            currentList.add(BudgetFormLineItem(producto = prod, cantidad = qty, precioUnitario = price))
        }

        lineItems.value = currentList
        closeLineConfiguration()
        return true
    }

    fun removeLineItem(index: Int) {
        if (index in lineItems.value.indices) {
            val currentList = lineItems.value.toMutableList()
            currentList.removeAt(index)
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

            val isBaseCurrency = companyBaseCurrencyId != null && moneda.id == companyBaseCurrencyId
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

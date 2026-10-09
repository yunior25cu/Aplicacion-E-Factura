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

enum class BudgetFormMode {
    CREATE,
    EDIT
}

data class BudgetFormLineItem(
    val producto: ProductoDto,
    var idSkuVariante: Long? = null,
    var indicadorFacturacionC4: Int? = null,
    var idPromocionSugerida: Long? = null,
    var descuentoManual: Boolean = false,
    var cantidad: Double = 1.0,
    var precioUnitario: Double = producto.precio ?: 0.0,
    var descuento: Double = 0.0,
    val originalDocProductSnapshot: BudgetDocumentProductDto? = null
)

sealed class BudgetFormUiState {
    object Idle : BudgetFormUiState()
    object Loading : BudgetFormUiState()
    data class Success(val budgetId: Long, val isEdit: Boolean = false) : BudgetFormUiState()
    data class Error(val message: String) : BudgetFormUiState()
}

class BudgetFormViewModel(
    private val budgetRepository: BudgetRepository,
    private val cfeRepository: CfeRepository
) : ViewModel() {

    var uiState by mutableStateOf<BudgetFormUiState>(BudgetFormUiState.Idle)
        private set

    var formMode by mutableStateOf(BudgetFormMode.CREATE)
    var editingBudgetId by mutableStateOf<Long?>(null)
    var originalBudgetSnapshot by mutableStateOf<BudgetDto?>(null)

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
    var indicadoresC4List by mutableStateOf<List<CfeFiscalIndicadorFacturacionDto>>(emptyList())

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
                if (it.isNotEmpty() && selectedAlmacen == null && formMode == BudgetFormMode.CREATE) {
                    selectedAlmacen = it.first()
                }
            }

            cfeRepository.getTasaCambios(fechaConfirmacion).onSuccess { tasas ->
                tasasCambioList = tasas
                if (formMode == BudgetFormMode.CREATE) {
                    resolveSelectedMoneda(tasas)
                }
            }

            cfeRepository.getProductos().onSuccess { productosList = it }
            cfeRepository.getIndicadoresFacturacion(111, 1, "A", fechaConfirmacion).onSuccess {
                if (it != null) indicadoresC4List = it
            }
            isCatalogsLoading = false
        }
    }

    fun loadBudgetForEdit(budgetId: Long) {
        viewModelScope.launch {
            uiState = BudgetFormUiState.Loading

            cfeRepository.getEmpresa().onSuccess { empresa ->
                companyBaseCurrencyId = empresa.moneda?.id
            }

            val result = budgetRepository.getBudgetById(budgetId)
            result.onSuccess { budget ->
                if (budget.estado != BudgetEstado.SIN_CONFIRMAR.code || budget.factura != null) {
                    uiState = BudgetFormUiState.Error("Este presupuesto no puede ser editado porque ya no se encuentra en estado Sin Confirmar o ya fue facturado.")
                    return@launch
                }

                formMode = BudgetFormMode.EDIT
                editingBudgetId = budgetId
                originalBudgetSnapshot = budget

                // Pre-populate exact form values
                fechaEmision = budget.fechaEmision.take(10)
                fechaConfirmacion = (budget.fechaConfirmacion ?: budget.fechaEmision).take(10)
                fechaVencimiento = budget.fechaVencimiento.take(10)

                selectedCliente = budget.cliente
                selectedAlmacen = budget.almacen
                preciosIncluyenIva = budget.preciosIncluyenIva

                numeroReferencia = budget.numeroReferencia ?: ""
                nota = budget.nota ?: ""
                terminoCondiciones = budget.terminoCondiciones ?: ""

                val budgetMoneda = budget.moneda
                if (budgetMoneda != null) {
                    val tasaItem = TasaCambioSimpleDto(
                        id = budgetMoneda.id,
                        codigo = budgetMoneda.codigo ?: "UYU",
                        denominacion = budgetMoneda.nombre,
                        simbolo = "$",
                        decimales = 2,
                        tasaPromedio = budget.tasaCambio
                    )
                    selectedMoneda = tasaItem
                    tasaCambio = budget.tasaCambio
                }

                val isBaseCurrency = companyBaseCurrencyId == null || (budget.moneda?.id == companyBaseCurrencyId)

                val convertedLines = budget.documentoProductos.map { docProd ->
                    val lineUnitPrice = if (isBaseCurrency) {
                        if (budget.preciosIncluyenIva && (docProd.precioBaseConIva ?: 0.0) > 0) {
                            docProd.precioBaseConIva!!
                        } else {
                            docProd.precioBase
                        }
                    } else {
                        if (budget.preciosIncluyenIva && (docProd.precioOriginalConIva ?: 0.0) > 0) {
                            docProd.precioOriginalConIva!!
                        } else {
                            if ((docProd.precioOriginal) > 0) docProd.precioOriginal else docProd.precioBase
                        }
                    }

                    val lineDiscount = if (isBaseCurrency) {
                        docProd.descuento
                    } else {
                        docProd.descuentoOriginal ?: docProd.descuento
                    }

                    val prod = docProd.producto ?: ProductoDto(
                        id = docProd.idProducto?.toInt() ?: 0,
                        nombre = docProd.descripcion ?: "Producto",
                        codigo = docProd.codigo,
                        precio = lineUnitPrice,
                        tasaIva = docProd.porcentajeIva
                    )

                    BudgetFormLineItem(
                        producto = prod,
                        idSkuVariante = docProd.idSkuVariante,
                        indicadorFacturacionC4 = docProd.indicadorFacturacionC4,
                        idPromocionSugerida = docProd.idPromocionSugerida,
                        descuentoManual = docProd.descuentoManual,
                        cantidad = docProd.cantidad,
                        precioUnitario = lineUnitPrice,
                        descuento = lineDiscount,
                        originalDocProductSnapshot = docProd
                    )
                }
                lineItems.value = convertedLines

                uiState = BudgetFormUiState.Idle
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                uiState = BudgetFormUiState.Error(appError.getDisplayMessage())
            }
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

    fun addLineItem(producto: ProductoDto, cantidad: Double = 1.0, precioUnitario: Double = producto.precio ?: 0.0) {
        val currentList = lineItems.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.producto.id == producto.id }
        if (existingIndex >= 0) {
            val item = currentList[existingIndex]
            currentList[existingIndex] = item.copy(cantidad = item.cantidad + cantidad, precioUnitario = precioUnitario)
        } else {
            currentList.add(BudgetFormLineItem(producto = producto, cantidad = cantidad, precioUnitario = precioUnitario))
        }
        lineItems.value = currentList
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

    fun confirmLineConfiguration(quantityStr: String, priceStr: String, indicadorC4: Int? = null): Boolean {
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
                precioUnitario = price,
                indicadorFacturacionC4 = indicadorC4 ?: currentList[index].indicadorFacturacionC4
            )
        } else {
            currentList.add(BudgetFormLineItem(
                producto = prod,
                cantidad = qty,
                precioUnitario = price,
                indicadorFacturacionC4 = indicadorC4
            ))
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
            val taxRate = item.producto.effectiveTaxRate
            if (preciosIncluyenIva && taxRate > 0) {
                totalLine / (1 + taxRate)
            } else {
                totalLine
            }
        }
    }

    fun calculateIva(): Double {
        return lineItems.value.sumOf { item ->
            val totalLine = item.cantidad * item.precioUnitario - item.descuento
            val taxRate = item.producto.effectiveTaxRate
            if (taxRate == 0.0) {
                0.0
            } else if (preciosIncluyenIva) {
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

        // Defensive Safety Guard: Prevent silent data corruption in EDIT mode
        if (formMode == BudgetFormMode.EDIT) {
            val originalTotal = originalBudgetSnapshot?.importeTotalBase ?: originalBudgetSnapshot?.importeTotalOriginal ?: 0.0
            if (originalTotal > 0.0 && calculateTotal() == 0.0) {
                uiState = BudgetFormUiState.Error("Error de validación: Se detectó un total cero inesperado durante la edición. Por favor verifique las líneas antes de guardar.")
                return
            }
        }

        viewModelScope.launch {
            uiState = BudgetFormUiState.Loading

            val isBaseCurrency = companyBaseCurrencyId != null && moneda.id == companyBaseCurrencyId
            val rate = if (isBaseCurrency) 1.0 else (if (tasaCambio > 0) tasaCambio else 1.0)

            val calculatedSubtotal = calculateSubtotal()
            val calculatedIva = calculateIva()
            val calculatedTotal = calculateTotal()

            val subtotalBase = if (isBaseCurrency) calculatedSubtotal else calculatedSubtotal * rate
            val ivaBase = if (isBaseCurrency) calculatedIva else calculatedIva * rate
            val totalBase = if (isBaseCurrency) calculatedTotal else calculatedTotal * rate

            if (formMode == BudgetFormMode.CREATE) {
                val createDocumentProducts = lineItems.value.map { item ->
                    val taxRate = item.producto.effectiveTaxRate
                    val lineTotalInput = item.cantidad * item.precioUnitario - item.descuento

                    val lineSubtotalDocCurrency: Double
                    val lineIvaDocCurrency: Double
                    val lineTotalWithIvaDocCurrency: Double

                    if (taxRate == 0.0) {
                        lineSubtotalDocCurrency = lineTotalInput
                        lineIvaDocCurrency = 0.0
                        lineTotalWithIvaDocCurrency = lineTotalInput
                    } else if (preciosIncluyenIva) {
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
                    val ivaBaseLine = if (isBaseCurrency) lineIvaDocCurrency else lineIvaDocCurrency * rate

                    val precioBaseConIva = if (isBaseCurrency) {
                        if (taxRate == 0.0 || preciosIncluyenIva) item.precioUnitario else item.precioUnitario * (1 + taxRate)
                    } else {
                        (if (taxRate == 0.0 || preciosIncluyenIva) item.precioUnitario else item.precioUnitario * (1 + taxRate)) * rate
                    }

                    val importeBaseConIva = if (isBaseCurrency) lineTotalWithIvaDocCurrency else lineTotalWithIvaDocCurrency * rate

                    BudgetDocumentProductCreateDto(
                        idProducto = item.producto.id.toLong(),
                        cantidad = item.cantidad,
                        precioBase = precioBase,
                        importeBase = importeBase,
                        iva = ivaBaseLine,
                        descuento = if (isBaseCurrency) item.descuento else item.descuento * rate,
                        ivaOriginal = if (isBaseCurrency) 0.0 else lineIvaDocCurrency,
                        descuentoOriginal = if (isBaseCurrency) 0.0 else item.descuento,
                        precioBaseConIva = precioBaseConIva,
                        importeBaseConIva = importeBaseConIva,
                        precioOriginal = if (isBaseCurrency) 0.0 else item.precioUnitario,
                        importeOriginal = if (isBaseCurrency) 0.0 else lineSubtotalDocCurrency,
                        precioOriginalConIva = if (isBaseCurrency) 0.0 else (if (taxRate == 0.0 || preciosIncluyenIva) item.precioUnitario else item.precioUnitario * (1 + taxRate)),
                        importeOriginalConIva = if (isBaseCurrency) 0.0 else lineTotalWithIvaDocCurrency,
                        indicadorFacturacionC4 = item.indicadorFacturacionC4
                    )
                }

                val createDto = BudgetCreateDto(
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
                    documentoProductos = createDocumentProducts
                )

                val result = budgetRepository.createBudget(createDto)
                result.onSuccess { createdId ->
                    uiState = BudgetFormUiState.Success(createdId, isEdit = false)
                }.onFailure { throwable ->
                    val appError = ErrorMapper.fromThrowable(throwable)
                    uiState = BudgetFormUiState.Error(appError.getDisplayMessage())
                }
            } else {
                // EDIT mode -> Build BudgetUpdateDto
                val updateBudgetId = editingBudgetId ?: return@launch
                val snapshot = originalBudgetSnapshot

                val updateDocumentProducts = lineItems.value.map { item ->
                    val taxRate = item.producto.effectiveTaxRate
                    val lineTotalInput = item.cantidad * item.precioUnitario - item.descuento

                    val lineSubtotalDocCurrency: Double
                    val lineIvaDocCurrency: Double
                    val lineTotalWithIvaDocCurrency: Double

                    if (taxRate == 0.0) {
                        lineSubtotalDocCurrency = lineTotalInput
                        lineIvaDocCurrency = 0.0
                        lineTotalWithIvaDocCurrency = lineTotalInput
                    } else if (preciosIncluyenIva) {
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
                    val ivaBaseLine = if (isBaseCurrency) lineIvaDocCurrency else lineIvaDocCurrency * rate

                    val precioBaseConIva = if (isBaseCurrency) {
                        if (taxRate == 0.0 || preciosIncluyenIva) item.precioUnitario else item.precioUnitario * (1 + taxRate)
                    } else {
                        (if (taxRate == 0.0 || preciosIncluyenIva) item.precioUnitario else item.precioUnitario * (1 + taxRate)) * rate
                    }

                    val importeBaseConIva = if (isBaseCurrency) lineTotalWithIvaDocCurrency else lineTotalWithIvaDocCurrency * rate

                    BudgetDocumentProductUpdateDto(
                        idProducto = item.producto.id.toLong(),
                        idSkuVariante = item.idSkuVariante,
                        cantidad = item.cantidad,
                        precioBase = precioBase,
                        importeBase = importeBase,
                        iva = ivaBaseLine,
                        descuento = if (isBaseCurrency) item.descuento else item.descuento * rate,
                        ivaOriginal = if (isBaseCurrency) 0.0 else lineIvaDocCurrency,
                        descuentoOriginal = if (isBaseCurrency) 0.0 else item.descuento,
                        precioBaseConIva = precioBaseConIva,
                        importeBaseConIva = importeBaseConIva,
                        precioOriginal = if (isBaseCurrency) 0.0 else item.precioUnitario,
                        importeOriginal = if (isBaseCurrency) 0.0 else lineSubtotalDocCurrency,
                        precioOriginalConIva = if (isBaseCurrency) 0.0 else (if (taxRate == 0.0 || preciosIncluyenIva) item.precioUnitario else item.precioUnitario * (1 + taxRate)),
                        importeOriginalConIva = if (isBaseCurrency) 0.0 else lineTotalWithIvaDocCurrency,
                        indicadorFacturacionC4 = item.indicadorFacturacionC4,
                        idPromocionSugerida = item.idPromocionSugerida,
                        descuentoManual = item.descuentoManual
                    )
                }

                val updateDto = BudgetUpdateDto(
                    id = updateBudgetId,
                    fechaEmision = fechaEmision,
                    fechaConfirmacion = fechaConfirmacion,
                    fechaVencimiento = fechaVencimiento,
                    numeroReferencia = numeroReferencia.takeIf { it.isNotBlank() },
                    nota = nota.takeIf { it.isNotBlank() },
                    terminoCondiciones = terminoCondiciones.takeIf { it.isNotBlank() },
                    idMoneda = moneda.id.toLong(),
                    tasaCambio = rate,
                    importeBase = subtotalBase,
                    iva = ivaBase,
                    descuento = 0.0,
                    importeTotalBase = totalBase,
                    ajusteRedondeoBase = snapshot?.ajusteRedondeoBase,
                    importeOriginal = if (isBaseCurrency) 0.0 else calculatedSubtotal,
                    ivaOriginal = if (isBaseCurrency) 0.0 else calculatedIva,
                    descuentoOriginal = 0.0,
                    tipoDescuentoGlobal = snapshot?.tipoDescuentoGlobal,
                    valorDescuentoGlobal = snapshot?.valorDescuentoGlobal,
                    importeTotalOriginal = if (isBaseCurrency) 0.0 else calculatedTotal,
                    ajusteRedondeoOriginal = snapshot?.ajusteRedondeoOriginal,
                    idAlmacen = almacen.id.toLong(),
                    idCliente = cliente.id.toLong(),
                    idCentroCosto = snapshot?.centroCosto?.id?.toLong(),
                    preciosIncluyenIva = preciosIncluyenIva,
                    documentoProductos = updateDocumentProducts
                )

                val result = budgetRepository.updateBudget(updateDto)
                result.onSuccess {
                    uiState = BudgetFormUiState.Success(updateBudgetId, isEdit = true)
                }.onFailure { throwable ->
                    val appError = ErrorMapper.fromThrowable(throwable)
                    uiState = BudgetFormUiState.Error(appError.getDisplayMessage())
                }
            }
        }
    }

    fun resetState() {
        uiState = BudgetFormUiState.Idle
    }
}

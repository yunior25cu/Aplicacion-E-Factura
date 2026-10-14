package com.authvex.balaxysefactura.ui.screens.product

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.core.repository.ProductRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed interface ProductCreateUiState {
    object LoadingCatalogs : ProductCreateUiState
    object Ready : ProductCreateUiState
    object Saving : ProductCreateUiState
    data class Success(val createdProduct: ProductoDto) : ProductCreateUiState
    data class Error(val message: String) : ProductCreateUiState
}

class ProductCreateViewModel(
    private val productRepository: ProductRepository,
    private val cfeRepository: CfeRepository,
    val initialAlmacenId: Int? = null
) : ViewModel() {

    var uiState: ProductCreateUiState by mutableStateOf(ProductCreateUiState.LoadingCatalogs)
        private set

    var isService by mutableStateOf(false)
    var codigo by mutableStateOf("")
    var denominacion by mutableStateOf("")
    var precioVentaText by mutableStateOf("0.0")
    var precioIncluyeIva by mutableStateOf(false)
    var descripcionTecnica by mutableStateOf("")

    var selectedUnidadMedida by mutableStateOf<CatalogoItemDto?>(null)
    var selectedTipoProducto by mutableStateOf<CatalogoItemDto?>(null)
    var selectedAlmacen by mutableStateOf<CatalogoItemDto?>(null)
    var selectedImpuestoVenta by mutableStateOf<CatalogoItemDto?>(null)

    var unidadesList by mutableStateOf<List<CatalogoItemDto>>(emptyList())
    var tiposProductoList by mutableStateOf<List<CatalogoItemDto>>(emptyList())
    var almacenesList by mutableStateOf<List<CatalogoItemDto>>(emptyList())
    var impuestosVentaList by mutableStateOf<List<CatalogoItemDto>>(emptyList())

    var idInventarioTipoCuenta by mutableStateOf<Long?>(null)
        private set

    init {
        loadCatalogs()
    }

    fun loadCatalogs() {
        viewModelScope.launch {
            uiState = ProductCreateUiState.LoadingCatalogs

            cfeRepository.getEmpresa().onSuccess { empresa ->
                idInventarioTipoCuenta = empresa.empresaConfig?.idInventarioTipoCuenta
            }

            productRepository.getUnidadesMedida().onSuccess { list ->
                unidadesList = list
                if (selectedUnidadMedida == null && list.isNotEmpty()) {
                    val defaultUnit = list.find { item ->
                        val cod = (item.codigo ?: "").uppercase(Locale.US)
                        val nom = item.nombre.uppercase(Locale.US)
                        cod == "UN" || cod == "UNI" || nom.contains("UNIDAD") || nom.contains("UNIDADES")
                    }
                    selectedUnidadMedida = defaultUnit ?: list.firstOrNull()
                }
            }

            productRepository.getTiposProducto().onSuccess { list ->
                tiposProductoList = list
                if (selectedTipoProducto == null && list.isNotEmpty()) {
                    val defaultType = list.find { item ->
                        val cod = (item.codigo ?: "").uppercase(Locale.US)
                        val nom = item.nombre.uppercase(Locale.US)
                        cod == "MERC" || cod == "VENTA" || nom.contains("VENTA") || nom.contains("MERCADERI") || nom.contains("INSUMO")
                    }
                    selectedTipoProducto = defaultType ?: list.firstOrNull()
                }
            }

            cfeRepository.getAlmacenes().onSuccess { list ->
                almacenesList = list
                val matched = if (initialAlmacenId != null) list.find { it.id == initialAlmacenId } else null
                selectedAlmacen = matched ?: list.firstOrNull()
            }

            productRepository.getImpuestosVenta().onSuccess { list ->
                impuestosVentaList = list
            }

            uiState = ProductCreateUiState.Ready
        }
    }

    fun saveProduct() {
        val accountId = idInventarioTipoCuenta
        if (accountId == null) {
            uiState = ProductCreateUiState.Error("No hay una cuenta de inventario configurada para crear productos.")
            return
        }

        val cleanDenom = denominacion.trim()
        val cleanCode = codigo.trim()

        if (cleanDenom.isBlank()) {
            uiState = ProductCreateUiState.Error("El nombre del producto es requerido")
            return
        }

        if (cleanDenom.length > 150) {
            uiState = ProductCreateUiState.Error("El nombre no puede exceder los 150 caracteres")
            return
        }

        if (cleanCode.isBlank()) {
            uiState = ProductCreateUiState.Error("El SKU / Código es requerido")
            return
        }

        if (cleanCode.length > 150) {
            uiState = ProductCreateUiState.Error("El SKU no puede exceder los 150 caracteres")
            return
        }

        val unidad = selectedUnidadMedida
        if (unidad == null) {
            uiState = ProductCreateUiState.Error("Debe seleccionar una unidad de medida")
            return
        }

        val tipoProd = selectedTipoProducto
        if (tipoProd == null) {
            uiState = ProductCreateUiState.Error("Debe seleccionar un tipo de producto")
            return
        }

        if (!isService && selectedAlmacen == null) {
            uiState = ProductCreateUiState.Error("Debe seleccionar un almacén para el producto físico")
            return
        }

        val priceVal = precioVentaText.replace(',', '.').toDoubleOrNull()
        if (priceVal == null || priceVal < 0) {
            uiState = ProductCreateUiState.Error("El precio de venta debe ser un número válido")
            return
        }

        if (!isService && priceVal <= 0.0) {
            uiState = ProductCreateUiState.Error("El precio de venta debe ser mayor a cero para productos físicos")
            return
        }

        val modoPrecio = if (precioIncluyeIva) 1 else 0
        val precioConIva = if (precioIncluyeIva) priceVal else null

        val almacenesReq = if (!isService && selectedAlmacen != null) {
            listOf(AlmacenProductoRequest(idAlmacen = selectedAlmacen!!.id.toLong(), minStock = 0.0, maxStock = 0.0))
        } else {
            emptyList()
        }

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val request = ProductCreateRequest(
            codigo = cleanCode,
            denominacion = cleanDenom,
            esServicio = isService,
            precioVenta = priceVal,
            modoPrecioVenta = modoPrecio,
            precioVentaConIva = precioConIva,
            costo = 0.0,
            fechaEntrada = todayDate,
            idTipoProducto = tipoProd.id.toLong(),
            idUnidadMedida = unidad.id.toLong(),
            idTipoCuenta = accountId,
            idImpuestoVenta = selectedImpuestoVenta?.id?.toLong(),
            almacenProductos = almacenesReq,
            descripcionTecnica = descripcionTecnica.trim().takeIf { it.isNotBlank() }
        )

        uiState = ProductCreateUiState.Saving

        viewModelScope.launch {
            val createRes = productRepository.createProduct(request)
            createRes.onSuccess { createdId ->
                val getRes = productRepository.getProductById(createdId)
                getRes.onSuccess { createdProduct ->
                    uiState = ProductCreateUiState.Success(createdProduct)
                }.onFailure { throwable ->
                    val appError = ErrorMapper.fromThrowable(throwable)
                    uiState = ProductCreateUiState.Error(appError.getDisplayMessage())
                }
            }.onFailure { throwable ->
                val appError = ErrorMapper.fromThrowable(throwable)
                uiState = ProductCreateUiState.Error(appError.getDisplayMessage())
            }
        }
    }

    fun resetState() {
        uiState = ProductCreateUiState.Ready
    }
}

package com.authvex.balaxysefactura.core.network

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class AlmacenProductoRequest(
    val idAlmacen: Long,
    val minStock: Double? = 0.0,
    val maxStock: Double? = 0.0
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ProductCreateRequest(
    val codigo: String,
    val denominacion: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val esServicio: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val precioVenta: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val modoPrecioVenta: Int = 0,
    val precioVentaConIva: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val costo: Double = 0.0,
    val fechaEntrada: String,
    val idTipoProducto: Long,
    val idUnidadMedida: Long,
    val idTipoCuenta: Long,
    val idImpuestoVenta: Long? = null,
    val idImpuestoCompra: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val almacenProductos: List<AlmacenProductoRequest> = emptyList(),
    val descripcionTecnica: String? = null,
    val codigoBarras: String? = null,
    val codigoFabricacion: String? = null,
    val pesoUnitarioKg: Double? = null,
    val volumenUnitarioM3: Double? = null,
    val puntoReorden: Double? = null,
    val foto: String? = null,
    val idProducto: Long? = null,
    val idCategoria: Long? = null,
    val idSubcategoria: Long? = null,
    val idElementoGasto: Long? = null,
    val idSubelementoGasto: Long? = null,
    val idListaPrecioPredeterminada: Long? = null,
    val idListaPrecios: List<Long>? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val atributoValors: List<Long> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val activaVariantes: Boolean = false
)

@Serializable
data class ProductoExistenciaDto(
    val existencia: Double = 0.0,
    val pendienteEntrada: Double? = null,
    val pendienteSalida: Double? = null,
    val costo: Double? = null,
    val disponibles: Double? = null
)

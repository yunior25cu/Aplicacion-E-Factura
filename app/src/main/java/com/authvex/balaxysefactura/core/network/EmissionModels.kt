package com.authvex.balaxysefactura.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClienteDto(
    val id: Int,
    @SerialName("denominacion")
    val nombre: String,
    val ruc: String? = null,
    val direccion: String? = null
)

@Serializable
data class ProductoDto(
    val id: Int,
    @SerialName("denominacion")
    val nombre: String,
    val codigo: String? = null,
    val precio: Double? = null,
    val tasaIva: Double? = null
)

@Serializable
data class CatalogoItemDto(
    val id: Int,
    @SerialName("denominacion")
    val nombre: String,
    val codigo: String? = null
)

@Serializable
data class FacturaRequest(
    val clienteId: Int,
    val monedaId: Int,
    val fecha: String,
    val almacenId: Int,
    val formaPagoId: Int,
    val vencimientoId: Int? = null,
    val listaPrecioId: Int? = null,
    val vendedorId: Int? = null,
    val centroCostoId: Int? = null,
    val lineas: List<FacturaLineaRequest>,
    val notas: String? = null
)

@Serializable
data class FacturaLineaRequest(
    val productoId: Int,
    val cantidad: Double,
    val precioUnitario: Double,
    val descuento: Double = 0.0,
    val notas: String? = null
)

@Serializable
data class FacturaResponse(
    val documentoId: Long,
    val numero: String? = null,
    val total: Double? = null,
    val subtotal: Double? = null,
    val iva: Double? = null
)

@Serializable
data class CfeTipoPermitidoDto(
    val code: Int,
    val name: String,
    val suggested: Boolean,
    val implementedInUi: Boolean,
    val puntoVentaId: Int,
    val serie: String? = null,
    val proximoNumero: Long? = null
)

@Serializable
data class CfeEmitRequest(
    val puntoVentaId: Int,
    val seriePreferida: String?,
    val cfeCode: Int,
    val ncAdjustmentMode: Int? = null
)

@Serializable
data class CfeEmitResponse(
    val success: Boolean,
    val message: String? = null,
    val documentoId: Long? = null
)

@Serializable
data class CfeStatusResponse(
    val documentoId: Long,
    val estado: Int,
    val mensaje: String? = null,
    val caeNumero: String? = null,
    val caeVencimiento: String? = null
)

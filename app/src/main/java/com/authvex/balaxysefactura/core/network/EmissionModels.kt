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
    @SerialName("precioVenta")
    val precio: Double? = null,
    val tasaIva: Double? = null,
    val existencia: Double? = null,
    val esServicio: Boolean = false
)

@Serializable
data class CatalogoItemDto(
    val id: Int,
    @SerialName("denominacion")
    val nombre: String,
    val codigo: String? = null
)

@Serializable
data class FacturaCreateDto(
    val fechaEmision: String,
    val fechaConfirmacion: String,
    val idMoneda: Int,
    val tasaCambio: Double,
    val importeBase: Double,
    val iva: Double,
    val importeTotalBase: Double,
    val importeOriginal: Double,
    val ivaOriginal: Double,
    val importeTotalOriginal: Double,
    val idAlmacen: Int,
    val idCliente: Int,
    val documentoProductos: List<FacturaLineaRequest>,
    val numeroReferencia: String? = null,
    val nota: String? = null,
    val esElectronico: Boolean = true,
    val cobrar: Boolean = false,
    val idCuentaBanco: Int? = null,
    val idFormaPago: Int? = null,
    val idVencimiento: Int? = null,
    val idListaPrecio: Int? = null,
    val descuento: Double = 0.0,
    val ajusteRedondeoBase: Double = 0.0,
    val descuentoOriginal: Double = 0.0,
    val ajusteRedondeoOriginal: Double = 0.0,
    val idCentroCosto: Int? = null,
    val idVendedor: Int? = null
)

@Serializable
data class FacturaLineaRequest(
    val idProducto: Int,
    val cantidad: Double,
    val precioBase: Double,
    val importeBase: Double,
    val iva: Double,
    val descuento: Double = 0.0,
    val ivaOriginal: Double,
    val descuentoOriginal: Double,
    val precioBaseConIva: Double,
    val importeBaseConIva: Double,
    val precioOriginal: Double,
    val importeOriginal: Double,
    val precioOriginalConIva: Double,
    val importeOriginalConIva: Double,
    val indicadorFacturacionC4: Int? = null
)

@Serializable
data class DevolucionCreateDto(
    val fechaEmision: String,
    val fechaConfirmacion: String,
    val idMoneda: Int,
    val tasaCambio: Double,
    val importeBase: Double,
    val iva: Double,
    val importeTotalBase: Double,
    val importeOriginal: Double,
    val ivaOriginal: Double,
    val importeTotalOriginal: Double,
    val idAlmacen: Int,
    val idCliente: Int,
    val documentoProductos: List<FacturaLineaRequest>,
    val numeroReferencia: String? = null,
    val nota: String? = null,
    val esElectronico: Boolean = true,
    val cobrar: Boolean = false,
    val idCuentaBanco: Int? = null,
    val idFormaPago: Int? = null,
    val idVencimiento: Int? = null,
    val idListaPrecio: Int? = null,
    val descuento: Double = 0.0,
    val ajusteRedondeoBase: Double = 0.0,
    val descuentoOriginal: Double = 0.0,
    val ajusteRedondeoOriginal: Double = 0.0,
    val idCentroCosto: Int? = null,
    val idVendedor: Int? = null,
    val tipoDevolucion: String = "Factura",
    val naturalezaNota: String, // Credito o Debito
    val idDocumentoOrigen: Long
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
data class PuntoVentaDto(
    val id: Int,
    val nombre: String,
    val numero: Int,
    val activo: Boolean,
    val esPredeterminado: Boolean,
    val codigoSucursalDgi: String? = null
)

@Serializable
data class CfeFiscalDocumentAvailabilityGroupDto(
    val puntoVentaId: Int,
    val items: List<CfeFiscalDocumentAvailabilityItemDto>
)

@Serializable
data class CfeFiscalDocumentAvailabilityItemDto(
    val cfeCode: Int,
    val name: String,
    val habilitado: Boolean,
    val motivoNoHabilitado: String? = null,
    val puntoVentaId: Int,
    val serie: String? = null,
    val numeroDesde: Long? = null,
    val numeroHasta: Long? = null,
    val numeroActual: Long? = null,
    val vigenciaCae: String? = null
)

@Serializable
data class CfeValidateResponseDto(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val xmlPreview: String? = null
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
    val documentoId: Long? = null,
    val statusUrl: String? = null
)

@Serializable
data class CfeStatusResponse(
    val documentoId: Long,
    val estado: Int,
    val mensaje: String? = null,
    val caeNumero: String? = null,
    val caeVencimiento: String? = null
)

@Serializable
data class CaePrecheckResultDto(
    val hasValidCae: Boolean,
    val message: String? = null,
    val requiresNewCae: Boolean = false
)

@Serializable
data class CfeFiscalIndicadorFacturacionDto(
    @SerialName("value")
    val id: Int,
    @SerialName("label")
    val name: String,
    val description: String? = null,
    val code: String? = null
)

@Serializable
data class CfeFiscalIndicadorSugeridoDto(
    val persistedValue: Int?,
    val suggestedValue: Int?,
    val isAutomatic: Boolean,
    val label: String
)

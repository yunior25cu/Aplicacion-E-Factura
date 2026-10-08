package com.authvex.balaxysefactura.core.network

import kotlinx.serialization.Serializable

@Serializable
data class BudgetListResponse(
    val items: List<BudgetDto> = emptyList(),
    val totalRecords: Int = 0
)

@Serializable
data class BudgetDto(
    val id: Long = 0,
    val folio: String? = null,
    val numero: Int? = null,
    val fechaEmision: String = "",
    val fechaConfirmacion: String? = null,
    val fechaVencimiento: String = "",
    val numeroReferencia: String? = null,
    val esServicio: Boolean = false,
    val nota: String? = null,
    val terminoCondiciones: String? = null,
    val tasaCambio: Double = 1.0,
    val importeBase: Double? = null,
    val iva: Double? = null,
    val descuento: Double = 0.0,
    val importeTotalBase: Double? = null,
    val importeOriginal: Double? = null,
    val ivaOriginal: Double? = null,
    val descuentoOriginal: Double = 0.0,
    val importeTotalOriginal: Double? = null,
    val esElectronico: Boolean = false,
    val estado: Int = 1, // 1: SinConfirmar, 2: Confirmado, 3: Anulado, 4: Cancelado
    val moneda: CatalogoItemDto? = null,
    val almacen: CatalogoItemDto? = null,
    val cliente: ClienteDto? = null,
    val centroCosto: CatalogoItemDto? = null,
    val factura: BudgetFacturaDto? = null,
    val documentoProductos: List<BudgetDocumentProductDto> = emptyList()
)

@Serializable
data class BudgetFacturaDto(
    val id: Long = 0,
    val folio: String? = null,
    val fechaConfirmacion: String? = null,
    val iva: Double = 0.0,
    val importeBase: Double = 0.0,
    val descuento: Double? = null,
    val importeTotalBase: Double = 0.0
)

@Serializable
data class BudgetDocumentProductDto(
    val id: Long = 0,
    val idProducto: Long? = null,
    val cantidad: Double = 0.0,
    val descuento: Double = 0.0,
    val precioBase: Double = 0.0,
    val importeBase: Double? = null,
    val iva: Double? = null,
    val precioBaseConIva: Double? = null,
    val importeBaseConIva: Double? = null,
    val precioOriginal: Double = 0.0,
    val importeOriginal: Double? = null,
    val ivaOriginal: Double? = null,
    val precioOriginalConIva: Double? = null,
    val importeOriginalConIva: Double? = null,
    val descuentoOriginal: Double? = null,
    val codigo: String? = null,
    val descripcion: String? = null,
    val um: String? = null,
    val porcentajeIva: Double? = null,
    val producto: ProductoDto? = null
)

@Serializable
data class BudgetCreateDto(
    val fechaEmision: String,
    val fechaConfirmacion: String,
    val fechaVencimiento: String,
    val numeroReferencia: String? = null,
    val nota: String? = null,
    val terminoCondiciones: String? = null,
    val preciosIncluyenIva: Boolean = true,
    val esElectronico: Boolean = false,
    val idMoneda: Long,
    val tasaCambio: Double,
    val importeBase: Double,
    val iva: Double,
    val descuento: Double = 0.0,
    val importeTotalBase: Double,
    val importeOriginal: Double,
    val ivaOriginal: Double,
    val descuentoOriginal: Double = 0.0,
    val importeTotalOriginal: Double,
    val idAlmacen: Long,
    val idCliente: Long,
    val idCentroCosto: Long? = null,
    val documentoProductos: List<BudgetDocumentProductCreateDto>
)

@Serializable
data class BudgetDocumentProductCreateDto(
    val idProducto: Long,
    val cantidad: Double,
    val precioBase: Double,
    val importeBase: Double,
    val iva: Double,
    val descuento: Double = 0.0,
    val ivaOriginal: Double,
    val descuentoOriginal: Double = 0.0,
    val precioBaseConIva: Double,
    val importeBaseConIva: Double,
    val precioOriginal: Double,
    val importeOriginal: Double,
    val precioOriginalConIva: Double,
    val importeOriginalConIva: Double
)

@Serializable
data class BudgetInvoiceOptionsDto(
    val esElectronico: Boolean = false,
    val cfeCode: Int? = null,
    val tipoFiscal: String? = null,
    val puntosVenta: List<BudgetPuntoVentaDto> = emptyList()
)

@Serializable
data class BudgetPuntoVentaDto(
    val id: Long = 0,
    val nombre: String = "",
    val numero: Int = 0,
    val esPredeterminado: Boolean = false
)

@Serializable
data class BudgetFacturarDto(
    val fechaEmision: String? = null,
    val fechaConfirmacion: String? = null,
    val puntoVentaIdFiscal: Long? = null,
    val lineas: List<BudgetFacturarLineaDto>? = null
)

@Serializable
data class BudgetFacturarLineaDto(
    val idDocumentoProducto: Long,
    val cantidad: Double
)

enum class BudgetEstado(val code: Int, val label: String) {
    SIN_CONFIRMAR(1, "Sin Confirmar"),
    CONFIRMADO(2, "Confirmado"),
    ANULADO(3, "Anulado"),
    CANCELADO(4, "Cancelado");

    companion object {
        fun fromCode(code: Int): BudgetEstado {
            return entries.firstOrNull { it.code == code } ?: SIN_CONFIRMAR
        }
    }
}

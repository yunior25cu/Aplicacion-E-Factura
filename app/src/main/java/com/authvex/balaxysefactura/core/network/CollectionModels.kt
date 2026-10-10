package com.authvex.balaxysefactura.core.network

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
data class FacturaCobroCreateRequest(
    val idFactura: Long,
    val montoActualBase: Double,
    val montoBase: Double,
    val montoActualOriginal: Double,
    val montoOriginal: Double,
    val montoOperacion: Double
)

@Serializable
data class CobroCreateDto(
    val fechaEmision: String,
    val fechaConfirmacion: String,
    val numeroReferencia: String? = null,
    val nota: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("tipoDocumentoFinanza")
    val tipoDocumentoFinanza: Int = 1,
    val idCuentaBanco: Long? = null,
    val idFormaPago: Long,
    val idMoneda: Long,
    val tasaCambio: Double,
    val importeBase: Double,
    val iva: Double = 0.0,
    val montoTotalBase: Double,
    val importeOriginal: Double,
    val ivaOriginal: Double = 0.0,
    val montoTotalOriginal: Double,
    val idCliente: Long,
    val idCentroCosto: Long? = null,
    val facturaCobros: List<FacturaCobroCreateRequest> = emptyList()
)

@Serializable
data class CobroSummaryDto(
    val id: Long,
    val folio: String? = null,
    val numero: Int? = null,
    val fechaEmision: String,
    val fechaConfirmacion: String? = null,
    val clienteNombre: String? = null,
    val cliente: ClienteDto? = null,
    val monedaCodigo: String? = null,
    val moneda: CatalogoItemDto? = null,
    val formaPagoNombre: String? = null,
    val formaPago: CatalogoItemDto? = null,
    val estado: Int,
    val total: Double = 0.0,
    val montoTotalBase: Double? = null,
    val montoTotalOriginal: Double? = null
)

@Serializable
data class CobroListResponse(
    val totalRecords: Int = 0,
    val items: List<CobroSummaryDto> = emptyList()
)

@Serializable
data class CobroFacturaSummaryDto(
    val id: Long = 0,
    val folio: String? = null,
    val numero: Int? = null,
    val almacen: String? = null,
    val fechaEmision: String = "",
    val fechaConfirmacion: String = "",
    val importeTotalBase: Double? = null,
    val importeTotalOriginal: Double? = null
)

@Serializable
data class FacturaCobroDetailDto(
    val factura: CobroFacturaSummaryDto,
    val montoActualBase: Double = 0.0,
    val montoBase: Double = 0.0,
    val montoActualOriginal: Double = 0.0,
    val montoOriginal: Double = 0.0,
    val montoActualDocumento: Double? = null,
    val montoDocumento: Double? = null,
    val montoOperacion: Double? = null,
    val tasaCambioDocumentoCancelacion: Double? = null,
    val montoBaseCancelacion: Double? = null,
    val diferenciaCambioRealizada: Double? = null
)

@Serializable
data class CobroDetailDto(
    val id: Long = 0,
    val folio: String? = null,
    val numero: Int? = null,
    val fechaEmision: String = "",
    val fechaConfirmacion: String? = null,
    val estado: Int = 1,
    val cliente: ClienteDto? = null,
    val moneda: CatalogoItemDto? = null,
    val cuentaBanco: CuentaBancoSimpleDto? = null,
    val formaPago: CatalogoItemDto? = null,
    val tasaCambio: Double = 1.0,
    val numeroReferencia: String? = null,
    val nota: String? = null,
    val montoTotalBase: Double = 0.0,
    val montoTotalOriginal: Double = 0.0,
    val facturaCobros: List<FacturaCobroDetailDto> = emptyList()
)

@Serializable
data class CuentaBancoCatalogDto(
    val id: Long = 0,
    val proveedorBancario: CatalogoItemDto? = null,
    val numeroCuenta: String? = null,
    val tipoCuentaBanco: Int? = null,
    val moneda: CatalogoItemDto? = null,
    val idMoneda: Long? = null,
    val monedaCodigo: String? = null,
    val titular: String? = null,
    val iban: String? = null
) {
    fun getDisplayLabel(): String {
        val provName = proveedorBancario?.nombre?.takeIf { it.isNotBlank() }
        val num = numeroCuenta?.takeIf { it.isNotBlank() }
        val tit = titular?.takeIf { it.isNotBlank() }
        return when {
            tit != null && num != null -> "$tit ($num)"
            provName != null && num != null -> "$provName - $num"
            provName != null -> provName
            tit != null -> tit
            num != null -> "Cuenta N° $num"
            else -> "Cuenta N° $id"
        }
    }
}

@Serializable
data class CuentaBancoSimpleDto(
    val id: Long = 0,
    val numeroCuenta: String? = null,
    val tipoCuentaBanco: Int? = null,
    val moneda: CatalogoItemDto? = null,
    val titular: String? = null,
    val iban: String? = null
) {
    fun getDisplayLabel(): String {
        val num = numeroCuenta?.takeIf { it.isNotBlank() }
        val tit = titular?.takeIf { it.isNotBlank() }
        return when {
            tit != null && num != null -> "$tit ($num)"
            tit != null -> tit
            num != null -> "Cuenta N° $num"
            else -> "Cuenta N° $id"
        }
    }
}

@Serializable
data class CollectionInvoiceDto(
    val id: Long = 0,
    val folio: String? = null,
    val numero: Int? = null,
    val fechaEmision: String = "",
    val fechaConfirmacion: String? = null,
    val esElectronico: Boolean = true,
    val cfeCodeIntent: Int? = null,
    val estado: Int = 2,
    val moneda: CatalogoItemDto? = null,
    val tasaCambio: Double = 1.0,
    val importeTotalBase: Double? = null,
    val importeTotalOriginal: Double? = null,
    val importeTotalBaseCobrado: Double = 0.0,
    val importeTotalOriginalCobrado: Double = 0.0,
    val importeTotalBaseAjustado: Double = 0.0,
    val importeTotalOriginalAjustado: Double = 0.0,
    val devueltaCantidadBase: Double = 0.0,
    val devueltaCantidadOriginal: Double = 0.0,
    val devueltaPrecioBase: Double = 0.0,
    val devueltaPrecioOriginal: Double = 0.0,
    val notasDebitoBase: Double = 0.0,
    val notasDebitoOriginal: Double = 0.0,
    val cliente: ClienteDto? = null
) {
    fun calculatePendingBalances(): Pair<Double, Double> {
        val baseTotal = importeTotalBase ?: 0.0
        val baseDevoluciones = devueltaCantidadBase + devueltaPrecioBase
        val netTotalBase = max(baseTotal - baseDevoluciones + notasDebitoBase, 0.0)
        val collectedBase = importeTotalBaseCobrado + importeTotalBaseAjustado
        val pendingBase = max(netTotalBase - collectedBase, 0.0)

        val origTotal = importeTotalOriginal ?: 0.0
        val origDevoluciones = devueltaCantidadOriginal + devueltaPrecioOriginal
        val netTotalOrig = max(origTotal - origDevoluciones + notasDebitoOriginal, 0.0)
        val collectedOrig = importeTotalOriginalCobrado + importeTotalOriginalAjustado
        val pendingOrig = max(netTotalOrig - collectedOrig, 0.0)

        return Pair(pendingBase, pendingOrig)
    }
}

@Serializable
data class CollectionInvoiceListResponse(
    val totalRecords: Int = 0,
    val items: List<CollectionInvoiceDto> = emptyList()
)

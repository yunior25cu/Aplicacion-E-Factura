package com.authvex.balaxysefactura.core.network

import kotlinx.serialization.Serializable

@Serializable
data class PeriodoDto(
    val fechaDesde: String,
    val fechaHasta: String,
    val dias: Int
)

@Serializable
data class MonedaMetadataDto(
    val id: Int,
    val codigo: String,
    val denominacion: String,
    val simbolo: String
)

@Serializable
data class ReportsMetadataDto(
    val monedaBase: MonedaMetadataDto,
    val incluyeAnulados: Boolean,
    val incluyeSinConfirmar: Boolean,
    val origen: String,
    val timezone: String
)

@Serializable
data class VentasResumenResponse(
    val periodo: PeriodoDto,
    val totales: VentasTotalesDto,
    val comparacion: VentasComparacionDto? = null,
    val metadata: ReportsMetadataDto
)

@Serializable
data class VentasTotalesDto(
    val importeTotalBase: Double,
    val cantidadDocumentos: Int,
    val ticketPromedioBase: Double
)

@Serializable
data class VentasComparacionDto(
    val fechaDesdeAnterior: String,
    val fechaHastaAnterior: String,
    val importeTotalBaseAnterior: Double,
    val cantidadDocumentosAnterior: Int,
    val deltaImporteBase: Double,
    val deltaCantidadDocumentos: Int,
    val variacionImportePct: Double? = null,
    val variacionCantidadPct: Double? = null
)

@Serializable
data class VentasSerieResponse(
    val periodo: PeriodoDto,
    val granularidad: String,
    val puntos: List<VentaPuntoDto>,
    val metadata: ReportsMetadataDto
)

@Serializable
data class VentaPuntoDto(
    val fecha: String,
    val etiqueta: String,
    val importeTotalBase: Double,
    val cantidadDocumentos: Int
)

@Serializable
data class VentasPorClienteResponse(
    val periodo: PeriodoDto,
    val top: Int,
    val items: List<VentaClienteItemDto>,
    val metadata: ReportsMetadataDto
)

@Serializable
data class VentaClienteItemDto(
    val clienteId: Int,
    val codigo: String?,
    val denominacion: String,
    val importeTotalBase: Double,
    val cantidadDocumentos: Int
)

@Serializable
data class VentasPorProductoResponse(
    val periodo: PeriodoDto,
    val top: Int,
    val items: List<VentaProductoItemDto>,
    val metadata: ReportsMetadataDto
)

@Serializable
data class VentaProductoItemDto(
    val productoId: Int,
    val codigo: String?,
    val denominacion: String,
    val unidadMedidaCodigo: String?,
    val cantidad: Double,
    val importeTotalBase: Double
)

@Serializable
data class VentasPorDocumentoResponse(
    val periodo: PeriodoDto,
    val items: List<VentaDocumentoItemDto>,
    val metadata: ReportsMetadataDto
)

@Serializable
data class VentaDocumentoItemDto(
    val tipoDocumento: String,
    val etiqueta: String,
    val cantidadDocumentos: Int,
    val importeTotalBase: Double
)

@Serializable
data class CategoriaDto(
    val id: Int,
    val codigo: String?,
    val denominacion: String
)

@Serializable
data class ProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val traceId: String? = null
)

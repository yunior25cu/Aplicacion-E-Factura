package com.authvex.balaxysefactura.core.network

import kotlinx.serialization.Serializable

@Serializable
data class CuentasPorCobrarDto(
    val idCliente: Long = 0,
    val codigoCliente: String? = null,
    val denominacionCliente: String? = null,
    val id: Long? = null,
    val folio: String? = null,
    val fechaConfirmacion: String? = null,
    val numero: Int? = null,
    val numeroReferencia: String? = null,
    val origen: Int? = null,
    val importeTotal: Double = 0.0,
    val importeNotasCredito: Double = 0.0,
    val importeNotasDebito: Double = 0.0,
    val importeCobrado: Double = 0.0,
    val importeCobradoAplicado: Double = 0.0,
    val importeAjustado: Double = 0.0,
    val porCobrar: Double = 0.0,
    val saldoAFavor: Double = 0.0,
    val importeTotalOriginal: Double = 0.0,
    val importeNotasCreditoOriginal: Double = 0.0,
    val importeNotasDebitoOriginal: Double = 0.0,
    val importeCobradoOriginal: Double = 0.0,
    val importeCobradoOriginalAplicado: Double = 0.0,
    val importeAjustadoOriginal: Double = 0.0,
    val porCobrarOriginal: Double = 0.0,
    val saldoAFavorOriginal: Double = 0.0
)

@Serializable
data class CuentasPorCobrarAgingDto(
    val idCliente: Long = 0,
    val codigoCliente: String? = null,
    val denominacionCliente: String? = null,
    val cantidadDocumentos: Int = 0,
    val cantidadDocumentosFallback: Int = 0,
    val totalNoVencidas: Double = 0.0,
    val totalVencidas1a30: Double = 0.0,
    val totalVencidas31a60: Double = 0.0,
    val totalVencidas61a90: Double = 0.0,
    val totalVencidas91Mas: Double = 0.0,
    val total: Double = 0.0,
    val saldoAFavor: Double = 0.0,
    val fuenteVencimientoResumen: String? = null,
    val tieneFallbackVencimiento: Boolean = false
)

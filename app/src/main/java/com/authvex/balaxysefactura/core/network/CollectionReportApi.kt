package com.authvex.balaxysefactura.core.network

import retrofit2.http.*

interface CollectionReportApi {

    @GET("ReporteCuentasPorCobrar/GetCuentasPorCobrar")
    suspend fun getAccountsReceivable(
        @Query("FechaDesde") fechaDesde: String,
        @Query("FechaHasta") fechaHasta: String,
        @Query("IdAlmacen") idAlmacen: Long? = null,
        @Query("IdCliente") idCliente: Long? = null,
        @Query("IdFormaPago") idFormaPago: Long? = null,
        @Query("IdMoneda") idMoneda: Long? = null,
        @Query("Estado") estado: Int? = 1,
        @Query("EstadoVencimiento") estadoVencimiento: Int = 0,
        @Query("TipoReporte") tipoReporte: Int = 2
    ): List<CuentasPorCobrarDto>

    @GET("ReporteCuentasPorCobrar/GetCuentasPorCobrarAging")
    suspend fun getAccountsReceivableAging(
        @Query("FechaDesde") fechaDesde: String,
        @Query("FechaHasta") fechaHasta: String,
        @Query("IdAlmacen") idAlmacen: Long? = null,
        @Query("IdCliente") idCliente: Long? = null,
        @Query("IdFormaPago") idFormaPago: Long? = null,
        @Query("IdMoneda") idMoneda: Long? = null,
        @Query("Estado") estado: Int = 1,
        @Query("EstadoVencimiento") estadoVencimiento: Int = 0
    ): List<CuentasPorCobrarAgingDto>
}

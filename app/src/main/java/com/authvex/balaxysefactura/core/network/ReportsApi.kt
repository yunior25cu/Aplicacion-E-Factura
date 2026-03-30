package com.authvex.balaxysefactura.core.network

import retrofit2.http.*

interface ReportsApi {
    @GET("Informes/ventas/resumen")
    suspend fun getVentasResumen(
        @Query("fechaDesde") fechaDesde: String,
        @Query("fechaHasta") fechaHasta: String,
        @Query("almacenId") almacenId: Int? = null,
        @Query("clienteId") clienteId: Int? = null,
        @Query("soloElectronicas") soloElectronicas: Boolean? = null,
        @Query("compararPeriodoAnterior") compararPeriodoAnterior: Boolean = true
    ): VentasResumenResponse

    @GET("Informes/ventas/serie")
    suspend fun getVentasSerie(
        @Query("fechaDesde") fechaDesde: String,
        @Query("fechaHasta") fechaHasta: String,
        @Query("granularidad") granularidad: String? = null,
        @Query("almacenId") almacenId: Int? = null,
        @Query("clienteId") clienteId: Int? = null,
        @Query("soloElectronicas") soloElectronicas: Boolean? = null
    ): VentasSerieResponse

    @GET("Informes/ventas/por-cliente")
    suspend fun getVentasPorCliente(
        @Query("fechaDesde") fechaDesde: String,
        @Query("fechaHasta") fechaHasta: String,
        @Query("almacenId") almacenId: Int? = null,
        @Query("soloElectronicas") soloElectronicas: Boolean? = null,
        @Query("top") top: Int = 10
    ): VentasPorClienteResponse

    @GET("Informes/ventas/por-producto")
    suspend fun getVentasPorProducto(
        @Query("fechaDesde") fechaDesde: String,
        @Query("fechaHasta") fechaHasta: String,
        @Query("almacenId") almacenId: Int? = null,
        @Query("categoriaId") categoriaId: Int? = null,
        @Query("subcategoriaId") subcategoriaId: Int? = null,
        @Query("soloElectronicas") soloElectronicas: Boolean? = null,
        @Query("top") top: Int = 10
    ): VentasPorProductoResponse

    @GET("Informes/ventas/por-documento")
    suspend fun getVentasPorDocumento(
        @Query("fechaDesde") fechaDesde: String,
        @Query("fechaHasta") fechaHasta: String,
        @Query("almacenId") almacenId: Int? = null,
        @Query("soloElectronicas") soloElectronicas: Boolean? = null
    ): VentasPorDocumentoResponse

    @GET("Catalogo/Categorias")
    suspend fun getCategorias(@Query("Id") id: Int? = null): List<CategoriaDto>
}

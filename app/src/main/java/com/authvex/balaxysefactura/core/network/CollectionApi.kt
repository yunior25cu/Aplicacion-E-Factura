package com.authvex.balaxysefactura.core.network

import retrofit2.http.*

interface CollectionApi {

    @GET("Cobro")
    suspend fun getCobros(@QueryMap options: Map<String, String>): CobroListResponse

    @GET("Cobro/{id}")
    suspend fun getCobroById(@Path("id") id: Long): CobroDetailDto

    @POST("Cobro")
    suspend fun createCobro(@Body request: CobroCreateDto): Long

    @PUT("Cobro/confirmar/{id}")
    suspend fun confirmCobro(@Path("id") id: Long)

    @GET("Catalogo/CuentaBancos")
    suspend fun getCuentasBanco(): List<CuentaBancoCatalogDto>

    @GET("CuentaBanco/{id}/formas-pago/allowed")
    suspend fun getAllowedFormasPago(
        @Path("id") cuentaId: Long,
        @Query("tipoOperacion") tipoOperacion: Int = 1
    ): List<CatalogoItemDto>

    @GET("Factura")
    suspend fun getCollectableInvoices(
        @Query("IdCliente") idCliente: Long,
        @Query("Estado") estado: Int = 2,
        @Query("Liquidada") liquidada: Boolean = false,
        @Query("EsElectronico") esElectronico: Boolean = true,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 50
    ): CollectionInvoiceListResponse

    @GET("Factura/{id}")
    suspend fun getInvoiceById(@Path("id") id: Long): CollectionInvoiceDto
}

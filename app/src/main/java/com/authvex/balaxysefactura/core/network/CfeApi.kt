package com.authvex.balaxysefactura.core.network

import retrofit2.http.*

interface CfeApi {
    @POST("Cfe/search")
    suspend fun search(@Body request: CfeSearchRequest): CfeSearchResponse

    @GET("Cfe/documento/{documentoId}")
    suspend fun getDocument(@Path("documentoId") documentoId: Int): CfeDetailDto

    // --- Emisión ERP ---
    @POST("Factura")
    suspend fun createFactura(@Body request: FacturaRequest): Long

    @GET("Factura/{documentoId}")
    suspend fun getFactura(@Path("documentoId") documentoId: Long): FacturaResponse

    // --- Catálogos Paginados (Response: { totalRecords, items: [...] }) ---
    @GET("Cliente")
    suspend fun getClientes(@Query("filtro") filtro: String? = null): PagedResponse<ClienteDto>

    @GET("Producto")
    suspend fun getProductos(@Query("filtro") filtro: String? = null): PagedResponse<ProductoDto>

    // --- Catálogos de Lista Simple (Response: [...]) ---
    @GET("Catalogo/Monedas")
    suspend fun getMonedas(): List<CatalogoItemDto>

    @GET("Catalogo/TasaCambios")
    suspend fun getTasaCambio(@Query("Fecha") fecha: String): Double

    @GET("Catalogo/Almacens")
    suspend fun getAlmacenes(): List<CatalogoItemDto>

    @GET("Catalogo/FormaPagos")
    suspend fun getFormasPago(): List<CatalogoItemDto>

    @GET("Catalogo/Vencimientos")
    suspend fun getVencimientos(): List<CatalogoItemDto>

    @GET("Catalogo/ListaPrecios")
    suspend fun getListasPrecio(): List<CatalogoItemDto>

    @GET("Catalogo/Empleados")
    suspend fun getVendedores(@Query("cargoFuncional") cargo: String = "Vendedor"): List<CatalogoItemDto>

    @GET("Catalogo/CentroCostos")
    suspend fun getCentroCostos(): List<CatalogoItemDto>

    // --- Flujo Fiscal ---
    @GET("Cfe/fiscal/tipos-permitidos")
    suspend fun getTiposPermitidos(@Query("onlyImplemented") onlyImplemented: Boolean = true): List<CfeTipoPermitidoDto>

    @POST("Cfe/documento/{documentoId}/emit")
    suspend fun emitCfe(@Path("documentoId") documentoId: Long, @Body request: CfeEmitRequest): CfeEmitResponse

    @GET("Cfe/documento/{documentoId}/status")
    suspend fun getCfeStatus(@Path("documentoId") documentoId: Long): CfeStatusResponse
}

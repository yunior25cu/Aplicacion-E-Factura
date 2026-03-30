package com.authvex.balaxysefactura.core.network

import retrofit2.http.*

interface CfeApi {
    @POST("Cfe/search")
    suspend fun search(@Body request: CfeSearchRequest): CfeSearchResponse

    @GET("Cfe/documento/{documentoId}")
    suspend fun getDocument(@Path("documentoId") documentoId: Int): CfeDetailDto

    // --- Emisión ERP ---
    @POST("Factura")
    suspend fun createFactura(@Body request: FacturaCreateDto): Long

    @POST("Devolucion")
    suspend fun createDevolucion(@Body request: DevolucionCreateDto): Long

    @GET("Factura/{documentoId}")
    suspend fun getFactura(@Path("documentoId") documentoId: Long): FacturaResponse

    @GET("Devolucion/{documentoId}")
    suspend fun getDevolucion(@Path("documentoId") documentoId: Long): FacturaResponse

    // --- Catálogos Paginados ---
    @GET("Cliente")
    suspend fun getClientes(@Query("query") query: String? = null, @Query("limit") limit: Int = 20): PagedResponse<ClienteDto>

    @GET("Producto")
    suspend fun getProductos(@Query("query") query: String? = null, @Query("limit") limit: Int = 20): PagedResponse<ProductoDto>

    // --- Catálogos de Lista Simple ---
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

    // --- Flujo Fiscal Actualizado ---
    @GET("Cfe/puntos-venta")
    suspend fun getPuntosVenta(): List<PuntoVentaDto>

    @GET("Cfe/fiscal/documentos-habilitados")
    suspend fun getDocumentosHabilitados(@Query("punto_venta") puntoVentaId: Int): List<CfeFiscalDocumentAvailabilityGroupDto>

    @POST("Cfe/validate")
    suspend fun validateCfe(
        @Query("idDocumento") idDocumento: Long,
        @Query("cfeCode") cfeCode: Int,
        @Query("puntoVentaId") puntoVentaId: Int,
        @Query("seriePreferida") seriePreferida: String?
    ): CfeValidateResponseDto

    @POST("Cfe/documento/{documentoId}/emit")
    suspend fun emitCfe(@Path("documentoId") documentoId: Long, @Body request: CfeEmitRequest): CfeEmitResponse

    @GET("Cfe/documento/{documentoId}/status")
    suspend fun getCfeStatus(@Path("documentoId") documentoId: Long): CfeStatusResponse

    @GET
    suspend fun getCfeStatusByUrl(@Url statusUrl: String): CfeStatusResponse

    @GET("Cfe/documento/{documentoId}/status/sync")
    suspend fun getCfeStatusSync(@Path("documentoId") documentoId: Long): CfeStatusResponse

    @GET("Cfe/fiscal/tipos-permitidos")
    suspend fun getTiposPermitidos(@Query("onlyImplemented") onlyImplemented: Boolean = true): List<CfeTipoPermitidoDto>

    // --- Nuevos endpoints fiscales ---
    @GET("Cae/health/precheck")
    suspend fun caePrecheck(
        @Query("puntoVentaId") puntoVentaId: Int,
        @Query("tipoCfe") tipoCfe: Int,
        @Query("serie") serie: String?,
        @Query("fechaEmision") fechaEmision: String
    ): CaePrecheckResultDto

    @GET("Cfe/fiscal/indicadores-facturacion")
    suspend fun getIndicadoresFacturacion(
        @Query("cfeCode") cfeCode: Int,
        @Query("puntoVentaId") puntoVentaId: Int,
        @Query("seriePreferida") seriePreferida: String?,
        @Query("fechaEmision") fechaEmision: String
    ): List<CfeFiscalIndicadorFacturacionDto>

    @GET("Cfe/fiscal/indicadores-facturacion/sugerido")
    suspend fun getIndicadorSugerido(
        @Query("cfeCode") cfeCode: Int,
        @Query("tasaIva") tasaIva: Double,
        @Query("currentValue") currentValue: Int?,
        @Query("puntoVentaId") puntoVentaId: Int,
        @Query("seriePreferida") seriePreferida: String?,
        @Query("fechaEmision") fechaEmision: String
    ): CfeFiscalIndicadorSugeridoDto
}

package com.authvex.balaxysefactura.core.network

import retrofit2.http.*

interface ProductApi {

    @POST("Producto")
    suspend fun createProduct(@Body request: ProductCreateRequest): Long

    @GET("Producto/{id}")
    suspend fun getProductById(@Path("id") idProducto: Long): ProductoDto

    @GET("Catalogo/UnidadMedidas")
    suspend fun getUnidadesMedida(): List<CatalogoItemDto>

    @GET("Catalogo/TipoProductos")
    suspend fun getTiposProducto(): List<CatalogoItemDto>

    @GET("Catalogo/Impuestos")
    suspend fun getImpuestosVenta(
        @Query("IdAmbito") idAmbito: Int = 1,
        @Query("IdGrupo") idGrupo: Int = 1
    ): List<CatalogoItemDto>
}

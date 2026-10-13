package com.authvex.balaxysefactura.core.network

import retrofit2.http.*

interface ClientApi {

    @POST("Cliente")
    suspend fun createClient(@Body request: ClientCreateRequest): retrofit2.Response<Unit>

    @GET("Dgi/personaGetActEmpresarialTercero")
    suspend fun getThirdPartyRegistralData(
        @Query("rut") rut: String
    ): DgiPersonaActEmpresarialResponseDto
}

package com.authvex.balaxysefactura.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.QueryMap

interface BudgetApi {

    @GET("PreFactura")
    suspend fun getBudgets(@QueryMap filter: Map<String, String>): BudgetListResponse

    @GET("PreFactura/{id}")
    suspend fun getBudgetById(@Path("id") id: Long): BudgetDto

    @POST("PreFactura")
    suspend fun createBudget(@Body dto: BudgetCreateDto): Long

    @PUT("PreFactura")
    suspend fun updateBudget(@Body dto: BudgetUpdateDto)

    @PUT("PreFactura/confirmar/{id}")
    suspend fun confirmBudget(@Path("id") id: Long)

    @PUT("PreFactura/anular/{id}")
    suspend fun cancelBudget(@Path("id") id: Long)

    @GET("PreFactura/{id}/facturacion-opciones")
    suspend fun getInvoiceOptions(@Path("id") id: Long): BudgetInvoiceOptionsDto

    @PUT("PreFactura/facturar/{id}")
    suspend fun invoiceBudget(
        @Path("id") id: Long,
        @Body dto: BudgetFacturarDto
    ): Long
}

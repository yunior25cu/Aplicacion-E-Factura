package com.authvex.balaxysefactura.core.network

import retrofit2.http.GET

interface AccessApi {
    @GET("Access/me")
    suspend fun me(): AccessProfile
}

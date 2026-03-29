package com.authvex.balaxysefactura.core.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("Auth/login-token")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("Auth/refresh-token-body")
    fun refreshToken(@Body request: RefreshTokenRequest): Call<LoginResponse>
}

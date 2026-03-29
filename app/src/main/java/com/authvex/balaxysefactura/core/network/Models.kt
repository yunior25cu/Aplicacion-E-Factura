package com.authvex.balaxysefactura.core.network

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val correo: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Int,
    val expiresAt: String,
    val refreshToken: String,
    val loginInfo: LoginInfo? = null
)

@Serializable
data class LoginInfo(
    val empresaId: Int,
    val usuarioId: Int,
    val empresaNombre: String? = null,
    val usuarioNombre: String? = null
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class ApiErrorResponse(
    val message: String? = null,
    val title: String? = null,
    val detail: String? = null,
    val status: Int? = null
)

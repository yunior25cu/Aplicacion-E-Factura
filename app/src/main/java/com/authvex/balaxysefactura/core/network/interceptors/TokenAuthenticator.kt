package com.authvex.balaxysefactura.core.network.interceptors

import com.authvex.balaxysefactura.core.auth.AuthPreferences
import com.authvex.balaxysefactura.core.auth.SessionManager
import com.authvex.balaxysefactura.core.network.AuthApi
import com.authvex.balaxysefactura.core.network.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val authPreferences: AuthPreferences,
    private val authApiLazy: Lazy<AuthApi>
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Solo intentar refresh si el error es 401
        if (response.code != 401) return null

        // Evitar reintentos infinitos (máximo 1 reintento por request)
        if (responseCount(response) >= 2) {
            handleSessionExpired()
            return null
        }

        return runBlocking {
            mutex.withLock {
                val currentToken = authPreferences.getAuthTokenSync()
                val responseToken = response.request.header("Authorization")?.removePrefix("Bearer ")

                // Si el token ya cambió en medio de la espera del lock, reintentar con el nuevo
                if (currentToken != responseToken && !currentToken.isNullOrBlank()) {
                    return@runBlocking response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                }

                val refreshToken = authPreferences.getRefreshTokenSync()
                if (refreshToken.isNullOrBlank()) {
                    handleSessionExpired()
                    return@runBlocking null
                }

                try {
                    val refreshResponse = authApiLazy.value.refreshToken(RefreshTokenRequest(refreshToken)).execute()
                    
                    if (refreshResponse.isSuccessful && refreshResponse.body() != null) {
                        val newTokens = refreshResponse.body()!!
                        authPreferences.saveAuthData(
                            accessToken = newTokens.accessToken,
                            refreshToken = newTokens.refreshToken,
                            expiresAt = newTokens.expiresAt,
                            empresaId = newTokens.loginInfo?.empresaId ?: 0,
                            usuarioId = newTokens.loginInfo?.usuarioId ?: 0
                        )
                        
                        response.request.newBuilder()
                            .header("Authorization", "Bearer ${newTokens.accessToken}")
                            .build()
                    } else {
                        handleSessionExpired()
                        null
                    }
                } catch (e: Exception) {
                    handleSessionExpired()
                    null
                }
            }
        }
    }

    private fun handleSessionExpired() {
        runBlocking {
            authPreferences.clearAuthData()
            SessionManager.emitSessionExpired()
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var rs = response
        while (rs.priorResponse.also { if (it != null) rs = it } != null) {
            result++
        }
        return result
    }
}

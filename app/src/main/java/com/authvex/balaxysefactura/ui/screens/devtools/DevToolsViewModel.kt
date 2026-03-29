package com.authvex.balaxysefactura.ui.screens.devtools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.BuildConfig
import com.authvex.balaxysefactura.core.auth.AuthPreferences
import com.authvex.balaxysefactura.core.auth.AuthSession
import com.authvex.balaxysefactura.core.network.AuthApi
import com.authvex.balaxysefactura.core.network.ErrorMapper
import com.authvex.balaxysefactura.core.network.RefreshTokenRequest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class DevToolsUiState {
    object Idle : DevToolsUiState()
    object Loading : DevToolsUiState()
    data class Success(val message: String) : DevToolsUiState()
    data class Error(val message: String) : DevToolsUiState()
}

class DevToolsViewModel(
    private val authApi: AuthApi,
    private val authPreferences: AuthPreferences
) : ViewModel() {

    val session: StateFlow<AuthSession?> = authPreferences.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var uiState by mutableStateOf<DevToolsUiState>(DevToolsUiState.Idle)
        private set

    val baseUrl = BuildConfig.BASE_URL
    val isDebug = BuildConfig.DEBUG

    fun onForceRefresh() {
        viewModelScope.launch {
            uiState = DevToolsUiState.Loading
            try {
                val refreshToken = authPreferences.getRefreshTokenSync()
                if (refreshToken.isNullOrBlank()) {
                    uiState = DevToolsUiState.Error("No hay Refresh Token")
                    return@launch
                }

                // Usamos execute() para simular comportamiento síncrono del Authenticator si fuera necesario
                // Pero aquí en VM usamos la versión suspend si existiera, o manejamos el Call.
                val response = authApi.refreshToken(RefreshTokenRequest(refreshToken)).execute()
                
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    authPreferences.saveAuthData(
                        accessToken = data.accessToken,
                        refreshToken = data.refreshToken,
                        expiresAt = data.expiresAt,
                        empresaId = data.loginInfo?.empresaId ?: 0,
                        usuarioId = data.loginInfo?.usuarioId ?: 0
                    )
                    uiState = DevToolsUiState.Success("Token refrescado manualmente")
                } else {
                    uiState = DevToolsUiState.Error("Error en refresh: ${response.code()}")
                }
            } catch (e: Exception) {
                uiState = DevToolsUiState.Error(ErrorMapper.fromThrowable(e).getDisplayMessage())
            }
        }
    }

    fun onClearSession() {
        viewModelScope.launch {
            authPreferences.clearAuthData()
            uiState = DevToolsUiState.Success("Sesión limpiada")
        }
    }

    fun resetState() {
        uiState = DevToolsUiState.Idle
    }
}

package com.authvex.balaxysefactura.ui.screens.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.authvex.balaxysefactura.core.auth.AuthPreferences
import com.authvex.balaxysefactura.core.network.AuthApi
import com.authvex.balaxysefactura.core.network.ErrorMapper
import com.authvex.balaxysefactura.core.network.LoginRequest
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel(
    private val authApi: AuthApi,
    private val authPreferences: AuthPreferences
) : ViewModel() {

    var uiState by mutableStateOf<LoginUiState>(LoginUiState.Idle)
        private set

    var email by mutableStateOf("")
    var password by mutableStateOf("")

    fun onLoginClick() {
        if (email.isBlank() || password.isBlank()) {
            uiState = LoginUiState.Error("Correo y contraseña son requeridos")
            return
        }

        viewModelScope.launch {
            uiState = LoginUiState.Loading
            try {
                val response = authApi.login(LoginRequest(email, password))
                authPreferences.saveAuthData(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    expiresAt = response.expiresAt,
                    empresaId = response.loginInfo?.empresaId ?: 0,
                    usuarioId = response.loginInfo?.usuarioId ?: 0
                )
                uiState = LoginUiState.Success
            } catch (e: Exception) {
                val appError = ErrorMapper.fromThrowable(e)
                uiState = LoginUiState.Error(appError.getDisplayMessage())
            }
        }
    }

    fun resetState() {
        uiState = LoginUiState.Idle
    }
}

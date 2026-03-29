package com.authvex.balaxysefactura.core.auth

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

data class AuthSession(
    val accessToken: String?,
    val refreshToken: String?,
    val expiresAt: String?,
    val empresaId: String?,
    val usuarioId: String?
)

class AuthPreferences(private val context: Context) {
    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val EXPIRES_AT_KEY = stringPreferencesKey("expires_at")
        private val EMPRESA_ID_KEY = stringPreferencesKey("empresa_id")
        private val USUARIO_ID_KEY = stringPreferencesKey("usuario_id")
    }

    val session: Flow<AuthSession> = context.dataStore.data.map { preferences ->
        AuthSession(
            accessToken = preferences[ACCESS_TOKEN_KEY],
            refreshToken = preferences[REFRESH_TOKEN_KEY],
            expiresAt = preferences[EXPIRES_AT_KEY],
            empresaId = preferences[EMPRESA_ID_KEY],
            usuarioId = preferences[USUARIO_ID_KEY]
        )
    }

    val authToken: Flow<String?> = session.map { it.accessToken }
    val refreshToken: Flow<String?> = session.map { it.refreshToken }

    suspend fun getAuthTokenSync(): String? = authToken.first()
    suspend fun getRefreshTokenSync(): String? = refreshToken.first()

    suspend fun saveAuthData(
        accessToken: String,
        refreshToken: String,
        expiresAt: String,
        empresaId: Int,
        usuarioId: Int
    ) {
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessToken
            preferences[REFRESH_TOKEN_KEY] = refreshToken
            preferences[EXPIRES_AT_KEY] = expiresAt
            preferences[EMPRESA_ID_KEY] = empresaId.toString()
            preferences[USUARIO_ID_KEY] = usuarioId.toString()
        }
    }

    suspend fun clearAuthData() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

typealias MutablePreferences = androidx.datastore.preferences.core.MutablePreferences

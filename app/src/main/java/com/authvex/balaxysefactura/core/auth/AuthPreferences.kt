package com.authvex.balaxysefactura.core.auth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
        private val REMEMBER_ME_KEY = booleanPreferencesKey("remember_me")
        private val SAVED_EMAIL_KEY = stringPreferencesKey("saved_email")
    }

    val session: Flow<AuthSession> = context.dataStore.data.map { preferences ->
        AuthSession(
            accessToken = KeystoreCrypto.decrypt(preferences[ACCESS_TOKEN_KEY]),
            refreshToken = KeystoreCrypto.decrypt(preferences[REFRESH_TOKEN_KEY]),
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
            preferences[ACCESS_TOKEN_KEY] = KeystoreCrypto.encrypt(accessToken) ?: accessToken
            preferences[REFRESH_TOKEN_KEY] = KeystoreCrypto.encrypt(refreshToken) ?: refreshToken
            preferences[EXPIRES_AT_KEY] = expiresAt
            preferences[EMPRESA_ID_KEY] = empresaId.toString()
            preferences[USUARIO_ID_KEY] = usuarioId.toString()
        }
    }

    suspend fun saveRememberMeData(rememberMe: Boolean, email: String) {
        context.dataStore.edit { preferences ->
            preferences[REMEMBER_ME_KEY] = rememberMe
            if (rememberMe) {
                preferences[SAVED_EMAIL_KEY] = email
            } else {
                preferences.remove(SAVED_EMAIL_KEY)
            }
        }
    }

    suspend fun getSavedEmailSync(): String? = context.dataStore.data.map { it[SAVED_EMAIL_KEY] }.first()
    suspend fun getRememberMeSync(): Boolean = context.dataStore.data.map { it[REMEMBER_ME_KEY] ?: true }.first()

    suspend fun clearRememberMeData() {
        context.dataStore.edit { preferences ->
            preferences[REMEMBER_ME_KEY] = false
            preferences.remove(SAVED_EMAIL_KEY)
        }
    }

    suspend fun clearAuthData() {
        context.dataStore.edit { preferences ->
            val rememberMe = preferences[REMEMBER_ME_KEY] ?: true
            val savedEmail = preferences[SAVED_EMAIL_KEY]
            
            preferences.clear()
            
            if (rememberMe && !savedEmail.isNullOrBlank()) {
                preferences[REMEMBER_ME_KEY] = true
                preferences[SAVED_EMAIL_KEY] = savedEmail
            }
        }
    }
}

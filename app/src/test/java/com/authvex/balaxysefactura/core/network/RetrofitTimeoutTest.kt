package com.authvex.balaxysefactura.core.network

import com.authvex.balaxysefactura.core.auth.AuthPreferences
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class RetrofitTimeoutTest {

    @Test
    fun `verify assistant client has readTimeout at least 90s and general client remains at 30s`() {
        val authPreferences = mock<AuthPreferences>()
        val retrofitClient = RetrofitClient(authPreferences)

        val generalReadTimeoutMs = retrofitClient.okHttpClient.readTimeoutMillis
        val longRunningReadTimeoutMs = retrofitClient.longRunningOkHttpClient.readTimeoutMillis

        assertEquals(30000, generalReadTimeoutMs)
        assertTrue(longRunningReadTimeoutMs >= 90000)
        assertEquals(90000, longRunningReadTimeoutMs)
    }
}

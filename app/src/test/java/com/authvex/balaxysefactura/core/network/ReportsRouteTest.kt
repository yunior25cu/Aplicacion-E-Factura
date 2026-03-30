package com.authvex.balaxysefactura.core.network

import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

class ReportsRouteTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ReportsApi

    @Before
    fun setup() {
        server = MockWebServer()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(ReportsApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `verify ventas resumen route`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        try {
            api.getVentasResumen("2023-01-01", "2023-01-31")
        } catch (e: Exception) {
            // Ignoramos errores de parseo
        }
        val request = server.takeRequest()
        // El test original esperaba /api/v1/Informes/ventas/resumen...
        // Vamos a verificar qué está generando actualmente.
        assertEquals("/api/v1/Informes/ventas/resumen?fechaDesde=2023-01-01&fechaHasta=2023-01-31&compararPeriodoAnterior=true", request.path)
    }

    @Test
    fun `verify ventas serie route`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        try {
            api.getVentasSerie("2023-01-01", "2023-01-31")
        } catch (e: Exception) {}
        val request = server.takeRequest()
        assertEquals("/api/v1/Informes/ventas/serie?fechaDesde=2023-01-01&fechaHasta=2023-01-31", request.path)
    }

    @Test
    fun `verify ventas por cliente route`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        try {
            api.getVentasPorCliente("2023-01-01", "2023-01-31")
        } catch (e: Exception) {}
        val request = server.takeRequest()
        assertEquals("/api/v1/Informes/ventas/por-cliente?fechaDesde=2023-01-01&fechaHasta=2023-01-31&top=10", request.path)
    }
    
    @Test
    fun `verify ventas por producto route`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        try {
            api.getVentasPorProducto("2023-01-01", "2023-01-31")
        } catch (e: Exception) {}
        val request = server.takeRequest()
        assertEquals("/api/v1/Informes/ventas/por-producto?fechaDesde=2023-01-01&fechaHasta=2023-01-31&top=10", request.path)
    }
    
    @Test
    fun `verify ventas por documento route`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        try {
            api.getVentasPorDocumento("2023-01-01", "2023-01-31")
        } catch (e: Exception) {}
        val request = server.takeRequest()
        assertEquals("/api/v1/Informes/ventas/por-documento?fechaDesde=2023-01-01&fechaHasta=2023-01-31", request.path)
    }
}

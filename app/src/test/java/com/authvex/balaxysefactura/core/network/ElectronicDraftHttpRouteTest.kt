package com.authvex.balaxysefactura.core.network

import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class ElectronicDraftHttpRouteTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: CfeRepository

    @Before
    fun setup() {
        server = MockWebServer()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val api = retrofit.create(CfeApi::class.java)
        repository = CfeRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `electronic draft HTTP request for 111 e-Factura sends esElectronico true and route parameters`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":123}"""))

        val request = FacturaCreateDto(
            fechaEmision = "2026-10-07",
            fechaConfirmacion = "2026-10-07",
            idMoneda = 33,
            tasaCambio = 1.0,
            importeBase = 100.0,
            iva = 22.0,
            importeTotalBase = 122.0,
            importeOriginal = 0.0,
            ivaOriginal = 0.0,
            importeTotalOriginal = 0.0,
            idAlmacen = 1,
            idCliente = 12,
            documentoProductos = emptyList(),
            esElectronico = true,
            cfeCodeIntent = 111,
            puntoVentaFiscalIntentId = 3,
            serieFiscalPreferidaIntent = "A",
            condicionPagoComercial = 1
        )

        val result = repository.createFacturaElectronicDraft(request)
        assertTrue(result.isSuccess)

        val recordedRequest = server.takeRequest()
        assertEquals("/api/v1/Factura/electronic-draft", recordedRequest.path)

        val bodyString = recordedRequest.body.readUtf8()
        val jsonObject = Json.parseToJsonElement(bodyString).jsonObject

        assertTrue("HTTP Body must explicitly include esElectronico", jsonObject.containsKey("esElectronico"))
        assertEquals(true, jsonObject["esElectronico"]?.jsonPrimitive?.boolean)
        assertEquals(111, jsonObject["cfeCodeIntent"]?.jsonPrimitive?.int)
        assertEquals(3, jsonObject["puntoVentaFiscalIntentId"]?.jsonPrimitive?.int)
        assertEquals("A", jsonObject["serieFiscalPreferidaIntent"]?.jsonPrimitive?.content)
        assertEquals(1, jsonObject["condicionPagoComercial"]?.jsonPrimitive?.int)
        assertEquals(33, jsonObject["idMoneda"]?.jsonPrimitive?.int)
    }

    @Test
    fun `electronic draft HTTP request for 101 e-Ticket sends esElectronico true and route parameters`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":124}"""))

        val request = FacturaCreateDto(
            fechaEmision = "2026-10-07",
            fechaConfirmacion = "2026-10-07",
            idMoneda = 33,
            tasaCambio = 1.0,
            importeBase = 50.0,
            iva = 11.0,
            importeTotalBase = 61.0,
            importeOriginal = 0.0,
            ivaOriginal = 0.0,
            importeTotalOriginal = 0.0,
            idAlmacen = 1,
            idCliente = 12,
            documentoProductos = emptyList(),
            esElectronico = true,
            cfeCodeIntent = 101,
            puntoVentaFiscalIntentId = 3,
            serieFiscalPreferidaIntent = "A",
            condicionPagoComercial = 1
        )

        val result = repository.createFacturaElectronicDraft(request)
        assertTrue(result.isSuccess)

        val recordedRequest = server.takeRequest()
        assertEquals("/api/v1/Factura/electronic-draft", recordedRequest.path)

        val bodyString = recordedRequest.body.readUtf8()
        val jsonObject = Json.parseToJsonElement(bodyString).jsonObject

        assertTrue("HTTP Body must explicitly include esElectronico", jsonObject.containsKey("esElectronico"))
        assertEquals(true, jsonObject["esElectronico"]?.jsonPrimitive?.boolean)
        assertEquals(101, jsonObject["cfeCodeIntent"]?.jsonPrimitive?.int)
        assertEquals(3, jsonObject["puntoVentaFiscalIntentId"]?.jsonPrimitive?.int)
        assertEquals("A", jsonObject["serieFiscalPreferidaIntent"]?.jsonPrimitive?.content)
        assertEquals(1, jsonObject["condicionPagoComercial"]?.jsonPrimitive?.int)
        assertEquals(33, jsonObject["idMoneda"]?.jsonPrimitive?.int)
    }
}

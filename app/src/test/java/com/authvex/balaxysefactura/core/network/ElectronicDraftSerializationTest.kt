package com.authvex.balaxysefactura.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import org.junit.Assert.*
import org.junit.Test

class ElectronicDraftSerializationTest {

    // Json config identical to RetrofitClient
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `electronic draft request for 111 e-Factura serializes esElectronico as true in JSON`() {
        val request = FacturaCreateDto(
            fechaEmision = "2026-10-07",
            fechaConfirmacion = "2026-10-07",
            idMoneda = 33,
            tasaCambio = 1.0,
            importeBase = 1.0,
            iva = 0.0,
            importeTotalBase = 1.0,
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

        val jsonString = json.encodeToString(FacturaCreateDto.serializer(), request)
        val jsonObject = json.parseToJsonElement(jsonString).jsonObject

        assertTrue("JSON must contain 'esElectronico' field", jsonObject.containsKey("esElectronico"))
        assertEquals(true, jsonObject["esElectronico"]?.jsonPrimitive?.boolean)
        assertEquals(111, jsonObject["cfeCodeIntent"]?.jsonPrimitive?.int)
        assertEquals(3, jsonObject["puntoVentaFiscalIntentId"]?.jsonPrimitive?.int)
        assertEquals("A", jsonObject["serieFiscalPreferidaIntent"]?.jsonPrimitive?.content)
    }

    @Test
    fun `electronic draft request for 101 e-Ticket serializes esElectronico as true in JSON`() {
        val request = FacturaCreateDto(
            fechaEmision = "2026-10-07",
            fechaConfirmacion = "2026-10-07",
            idMoneda = 33,
            tasaCambio = 1.0,
            importeBase = 1.0,
            iva = 0.0,
            importeTotalBase = 1.0,
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

        val jsonString = json.encodeToString(FacturaCreateDto.serializer(), request)
        val jsonObject = json.parseToJsonElement(jsonString).jsonObject

        assertTrue("JSON must contain 'esElectronico' field", jsonObject.containsKey("esElectronico"))
        assertEquals(true, jsonObject["esElectronico"]?.jsonPrimitive?.boolean)
        assertEquals(101, jsonObject["cfeCodeIntent"]?.jsonPrimitive?.int)
        assertEquals(3, jsonObject["puntoVentaFiscalIntentId"]?.jsonPrimitive?.int)
        assertEquals("A", jsonObject["serieFiscalPreferidaIntent"]?.jsonPrimitive?.content)
    }
}

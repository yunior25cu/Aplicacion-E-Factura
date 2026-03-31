package com.authvex.balaxysefactura.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportsParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parse VentasResumenResponse with null comparison percentages`() {
        val rawJson = """
            {
                "periodo": {
                    "fechaDesde": "2023-01-01",
                    "fechaHasta": "2023-01-31",
                    "dias": 31
                },
                "totales": {
                    "importeTotalBase": 1000.0,
                    "cantidadDocumentos": 10,
                    "ticketPromedioBase": 100.0
                },
                "comparacion": {
                    "fechaDesdeAnterior": "2022-12-01",
                    "fechaHastaAnterior": "2022-12-31",
                    "importeTotalBaseAnterior": 0.0,
                    "cantidadDocumentosAnterior": 0,
                    "deltaImporteBase": 1000.0,
                    "deltaCantidadDocumentos": 10,
                    "variacionImportePct": null,
                    "variacionCantidadPct": null
                },
                "metadata": {
                    "monedaBase": {
                        "id": 1,
                        "codigo": "UYU",
                        "denominacion": "Peso",
                        "simbolo": "$"
                    },
                    "incluyeAnulados": false,
                    "incluyeSinConfirmar": false,
                    "origen": "erp",
                    "timezone": "UTC"
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<VentasResumenResponse>(rawJson)

        assertEquals(1000.0, response.totales.importeTotalBase, 0.0)
        assertNull(response.comparacion?.variacionImportePct)
        assertNull(response.comparacion?.variacionCantidadPct)
    }
}

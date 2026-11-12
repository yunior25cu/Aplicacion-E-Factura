package com.authvex.balaxysefactura.ui.screens.assistant.cards

import com.authvex.balaxysefactura.core.network.AiTarjeta
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AiCardModelTest {

    @Test
    fun `buildCard hides hidden keys and formats title, kpis and summaries`() {
        val jsonDatos = buildJsonObject {
            put("titulo", "Deuda Vencida")
            put("id_cliente", 436)
            put("token_confirmacion", "secret-token")
            put("nota", "Se muestran 20 de 94. Usa filtros o aumenta 'limite' (max 50).")
            put("total_por_cobrar", 1524963.2)
            put("moneda", "UYU")
            put("activo", true)
            put("url_erp", "https://nice-ocean-0b5c5570f.5.azurestaticapps.net/clients/details_sale/1043")
        }

        val tarjeta = AiTarjeta(tool = "deuda_vencida", datos = jsonDatos)

        val card = buildCard(tarjeta)

        assertEquals("deuda_vencida", card.tool)
        assertEquals("Deuda Vencida", card.title)
        assertEquals("https://nice-ocean-0b5c5570f.5.azurestaticapps.net/clients/details_sale/1043", card.erpUrl)

        // Verify KPI formatting es-UY
        assertEquals(1, card.kpis.size)
        assertEquals("Total por cobrar", card.kpis[0].label)
        assertEquals("1.524.963,2", card.kpis[0].value)

        // Verify Summaries (id_cliente, token_confirmacion, nota, url_erp hidden)
        val summaryKeys = card.summaries.map { it.label }
        assertFalse(summaryKeys.contains("Id cliente"))
        assertFalse(summaryKeys.contains("Token confirmacion"))
        assertFalse(summaryKeys.contains("Nota"))
        assertFalse(summaryKeys.contains("Url erp"))

        val monedaItem = card.summaries.find { it.label == "Moneda" }
        assertNotNull(monedaItem)
        assertEquals("UYU", monedaItem?.value)

        val activoItem = card.summaries.find { it.label == "Activo" }
        assertNotNull(activoItem)
        assertEquals("Sí", activoItem?.value)
    }

    @Test
    fun `buildCard uses default title when missing`() {
        val tarjeta = AiTarjeta(tool = "default_tool", datos = JsonObject(emptyMap()))
        val card = buildCard(tarjeta)
        assertEquals("Balaxys", card.title)
    }

    @Test
    fun `buildCard parses table with max 4 columns and max 20 rows`() {
        val clientItems = (1..25).map { idx ->
            buildJsonObject {
                put("id_cliente", idx)
                put("cliente", "Cliente $idx")
                put("por_cobrar", idx * 1000.0)
                put("deuda_dias", idx * 5)
                put("score", "A")
                put("extra_col", "ignorable")
            }
        }

        val jsonDatos = buildJsonObject {
            put("nota", "Se muestran 20 de 94. Usa filtros o aumenta 'limite' (max 50).")
            put("clientes", JsonArray(clientItems))
        }

        val tarjeta = AiTarjeta(tool = "cuentas_por_cobrar", datos = jsonDatos)
        val card = buildCard(tarjeta)

        assertNotNull(card.table)
        val tbl = card.table!!

        // Max 4 visible columns (id_cliente hidden)
        assertEquals(4, tbl.headers.size)
        assertEquals(20, tbl.rows.size)
        assertEquals("Mostrando 20 de 25", tbl.footerText)
        assertEquals("1.000", tbl.rows[0][1]) // es-UY number format
    }

    @Test
    fun `buildCard hides nota from table footer when rows are 20 or fewer`() {
        val clientItems = (1..20).map { idx ->
            buildJsonObject {
                put("cliente", "Cliente $idx")
                put("por_cobrar", idx * 1000.0)
            }
        }

        val jsonDatos = buildJsonObject {
            put("nota", "Se muestran 20 de 94. Usa filtros o aumenta 'limite' (max 50).")
            put("clientes", JsonArray(clientItems))
        }

        val tarjeta = AiTarjeta(tool = "cuentas_por_cobrar", datos = jsonDatos)
        val card = buildCard(tarjeta)

        assertNotNull(card.table)
        assertNull(card.table!!.footerText)
        assertTrue(card.summaries.none { it.label.equals("Nota", ignoreCase = true) })
    }

    @Test
    fun `buildCard parses critical and warning alerts`() {
        val jsonDatos = buildJsonObject {
            put(
                "alertas",
                buildJsonArray {
                    add(buildJsonObject {
                        put("titulo", "Factura vencida")
                        put("gravedad", "critica")
                    })
                    add(buildJsonObject {
                        put("titulo", "Insumo bajo stock")
                        put("gravedad", "advertencia")
                    })
                }
            )
        }

        val tarjeta = AiTarjeta(tool = "alertas_tool", datos = jsonDatos)
        val card = buildCard(tarjeta)

        assertEquals(2, card.alerts.size)
        assertEquals("Factura vencida", card.alerts[0].text)
        assertEquals(AlertSeverity.CRITICAL, card.alerts[0].severity)

        assertEquals("Insumo bajo stock", card.alerts[1].text)
        assertEquals(AlertSeverity.WARNING, card.alerts[1].severity)
    }

    @Test
    fun `buildCard parses confirm action`() {
        val confirmObj = buildJsonObject {
            put("herramienta", "confirmar_factura")
            put("argumentos", buildJsonObject {
                put("id_factura", 1043)
                put("token_confirmacion", "abc123token")
            })
        }

        val jsonDatos = buildJsonObject {
            put("confirmar_con", confirmObj)
        }

        val tarjeta = AiTarjeta(tool = "preparar_confirmacion", datos = jsonDatos)
        val card = buildCard(tarjeta)

        assertNotNull(card.confirmAction)
        assertEquals("confirmar_factura", card.confirmAction?.herramienta)
        assertEquals(1043, card.confirmAction?.argumentos?.jsonObject?.get("id_factura")?.jsonPrimitive?.int)
    }
}

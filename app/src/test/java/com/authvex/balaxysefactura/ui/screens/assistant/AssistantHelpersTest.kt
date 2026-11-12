package com.authvex.balaxysefactura.ui.screens.assistant

import com.authvex.balaxysefactura.core.network.CfeDetailDto
import com.authvex.balaxysefactura.core.network.ClienteDto
import com.authvex.balaxysefactura.core.network.CobroDetailDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class AssistantHelpersTest {

    @Test
    fun `sameConversationId compares case-insensitively with trim and rejects nulls or blanks`() {
        assertTrue(
            sameConversationId(
                "febb332f-5149-4797-8d15-58309c28a4ba",
                "FEBB332F-5149-4797-8D15-58309C28A4BA"
            )
        )
        assertTrue(
            sameConversationId(
                "  febb332f-5149-4797-8d15-58309c28a4ba ",
                " FEBB332F-5149-4797-8D15-58309C28A4BA  "
            )
        )
        assertFalse(sameConversationId("c-1", "c-2"))
        assertFalse(sameConversationId(null, "c-1"))
        assertFalse(sameConversationId("c-1", null))
        assertFalse(sameConversationId(null, null))
        assertFalse(sameConversationId("", ""))
        assertFalse(sameConversationId("   ", "c-1"))
        assertFalse(sameConversationId("c-1", "   "))
    }

    @Test
    fun `appendRecognizedSpeech handles empty input, existing text and whitespace`() {
        assertEquals("cuánto me deben", appendRecognizedSpeech("", "cuánto me deben"))
        assertEquals("cuánto me deben", appendRecognizedSpeech("   ", "  cuánto me deben  "))
        assertEquals("Hola cuánto me deben", appendRecognizedSpeech("Hola", "cuánto me deben"))
        assertEquals("Hola cuánto me deben", appendRecognizedSpeech("Hola   ", "  cuánto me deben  "))
        assertEquals("Hola", appendRecognizedSpeech("Hola", ""))
        assertEquals("Hola", appendRecognizedSpeech("Hola", "   "))
        assertEquals("Hola", appendRecognizedSpeech("Hola", null))
    }

    @Test
    fun `formatConversationDate converts UTC to local time and formats today, yesterday, date, null and invalid`() {
        val uyZone = TimeZone.getTimeZone("GMT-3")
        // Fixed "now": 2026-11-12T15:00:00Z -> 2026-11-12 12:00 in GMT-3
        val nowMillis = parseUtcTimestampMillis("2026-11-12T15:00:00")!!

        // 1. Today in UTC (03:56 UTC -> 00:56 local in GMT-3 on 2026-11-12)
        assertEquals(
            "hoy 00:56",
            formatConversationDate("2026-11-12T03:56:08.771", nowMillis, uyZone)
        )

        // 2. Yesterday due to timezone shift (2026-11-12T01:30:00 UTC -> 2026-11-11 22:30 in GMT-3)
        assertEquals(
            "ayer",
            formatConversationDate("2026-11-12T01:30:00.000", nowMillis, uyZone)
        )

        // 3. Older date (2026-11-05T14:20:00 UTC -> 05/11)
        assertEquals(
            "05/11",
            formatConversationDate("2026-11-05T14:20:00", nowMillis, uyZone)
        )

        // 4. Null and invalid values
        assertEquals("", formatConversationDate(null, nowMillis, uyZone))
        assertEquals("", formatConversationDate("", nowMillis, uyZone))
        assertEquals("", formatConversationDate("no-es-una-fecha", nowMillis, uyZone))
    }

    @Test
    fun `buildCfeAssistantPrompt formats complete and partial CFE data with internal id and without gaps`() {
        val fullDoc = CfeDetailDto(
            documentoId = 1234,
            serie = "A",
            numero = 22036L,
            receptor = "EL SANTORAL"
        )
        assertEquals(
            "Contame el estado de la factura A-22036 (id interno 1234) de EL SANTORAL: saldo pendiente, vencimiento y cobros aplicados.",
            buildCfeAssistantPrompt(fullDoc)
        )

        val noReceptor = CfeDetailDto(
            documentoId = 10,
            serie = "A",
            numero = 123L,
            receptor = "   "
        )
        assertEquals(
            "Contame el estado de la factura A-123 (id interno 10): saldo pendiente, vencimiento y cobros aplicados.",
            buildCfeAssistantPrompt(noReceptor)
        )

        val withoutSerieOrNumeroWithId = CfeDetailDto(
            documentoId = 1234,
            serie = null,
            numero = null,
            receptor = "EL SANTORAL"
        )
        assertEquals(
            "Contame el estado de la factura (id interno 1234) de EL SANTORAL: saldo pendiente, vencimiento y cobros aplicados.",
            buildCfeAssistantPrompt(withoutSerieOrNumeroWithId)
        )

        val withZeroId = CfeDetailDto(
            documentoId = 0,
            serie = "A",
            numero = 22036L,
            receptor = "EL SANTORAL"
        )
        assertEquals(
            "Contame el estado de la factura A-22036 de EL SANTORAL: saldo pendiente, vencimiento y cobros aplicados.",
            buildCfeAssistantPrompt(withZeroId)
        )

        val emptyDocZeroId = CfeDetailDto(documentoId = 0)
        assertEquals(
            "Contame el estado de la factura: saldo pendiente, vencimiento y cobros aplicados.",
            buildCfeAssistantPrompt(emptyDocZeroId)
        )
    }

    @Test
    fun `buildCollectionAssistantPrompt formats complete and partial collection data with internal id and without gaps`() {
        val withFolioAndClient = CobroDetailDto(
            id = 5678,
            folio = "CO-140/2025",
            numero = 140,
            cliente = ClienteDto(id = 1, nombre = "EDGAR ALTAMIRANDA")
        )
        assertEquals(
            "Resumime el cobro CO-140/2025 (id interno 5678) de EDGAR ALTAMIRANDA: qué facturas cancela y cómo se pagó.",
            buildCollectionAssistantPrompt(withFolioAndClient)
        )

        val withNumeroOnly = CobroDetailDto(
            id = 5,
            folio = null,
            numero = 102,
            cliente = null
        )
        assertEquals(
            "Resumime el cobro 102 (id interno 5): qué facturas cancela y cómo se pagó.",
            buildCollectionAssistantPrompt(withNumeroOnly)
        )

        val withClientOnlyAndId = CobroDetailDto(
            id = 5678,
            folio = "  ",
            numero = null,
            cliente = ClienteDto(id = 1, nombre = "CLUB MALVIN")
        )
        assertEquals(
            "Resumime el cobro (id interno 5678) de CLUB MALVIN: qué facturas cancela y cómo se pagó.",
            buildCollectionAssistantPrompt(withClientOnlyAndId)
        )

        val withZeroId = CobroDetailDto(
            id = 0,
            folio = "CO-140/2025",
            numero = 140,
            cliente = ClienteDto(id = 1, nombre = "EDGAR ALTAMIRANDA")
        )
        assertEquals(
            "Resumime el cobro CO-140/2025 de EDGAR ALTAMIRANDA: qué facturas cancela y cómo se pagó.",
            buildCollectionAssistantPrompt(withZeroId)
        )

        val emptyCobroZeroId = CobroDetailDto(id = 0)
        assertEquals(
            "Resumime el cobro: qué facturas cancela y cómo se pagó.",
            buildCollectionAssistantPrompt(emptyCobroZeroId)
        )
    }
}

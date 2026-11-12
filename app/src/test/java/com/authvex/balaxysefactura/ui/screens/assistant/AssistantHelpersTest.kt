package com.authvex.balaxysefactura.ui.screens.assistant

import com.authvex.balaxysefactura.core.network.CfeDetailDto
import com.authvex.balaxysefactura.core.network.ClienteDto
import com.authvex.balaxysefactura.core.network.CobroDetailDto
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class AssistantHelpersTest {

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
    fun `buildCfeAssistantPrompt formats complete and partial CFE data without gaps`() {
        val fullDoc = CfeDetailDto(
            documentoId = 10,
            serie = "A",
            numero = 123L,
            receptor = "ABITAB S A"
        )
        assertEquals(
            "Contame el estado de la factura A-123 de ABITAB S A: saldo pendiente, vencimiento y cobros aplicados.",
            buildCfeAssistantPrompt(fullDoc)
        )

        val noReceptor = CfeDetailDto(
            documentoId = 10,
            serie = "A",
            numero = 123L,
            receptor = "   "
        )
        assertEquals(
            "Contame el estado de la factura A-123: saldo pendiente, vencimiento y cobros aplicados.",
            buildCfeAssistantPrompt(noReceptor)
        )

        val onlyNumeroAndReceptor = CfeDetailDto(
            documentoId = 10,
            serie = null,
            numero = 456L,
            receptor = "BARRETO"
        )
        assertEquals(
            "Contame el estado de la factura 456 de BARRETO: saldo pendiente, vencimiento y cobros aplicados.",
            buildCfeAssistantPrompt(onlyNumeroAndReceptor)
        )

        val emptyDoc = CfeDetailDto(documentoId = 10)
        assertEquals(
            "Contame el estado de la factura: saldo pendiente, vencimiento y cobros aplicados.",
            buildCfeAssistantPrompt(emptyDoc)
        )
    }

    @Test
    fun `buildCollectionAssistantPrompt formats complete and partial collection data without gaps`() {
        val withFolioAndClient = CobroDetailDto(
            id = 5,
            folio = "COB-102",
            numero = 102,
            cliente = ClienteDto(id = 1, nombre = "CLUB MALVIN")
        )
        assertEquals(
            "Resumime el cobro COB-102 de CLUB MALVIN: qué facturas cancela y cómo se pagó.",
            buildCollectionAssistantPrompt(withFolioAndClient)
        )

        val withNumeroOnly = CobroDetailDto(
            id = 5,
            folio = null,
            numero = 102,
            cliente = null
        )
        assertEquals(
            "Resumime el cobro 102: qué facturas cancela y cómo se pagó.",
            buildCollectionAssistantPrompt(withNumeroOnly)
        )

        val withClientOnly = CobroDetailDto(
            id = 5,
            folio = "  ",
            numero = null,
            cliente = ClienteDto(id = 1, nombre = "CLUB MALVIN")
        )
        assertEquals(
            "Resumime el cobro de CLUB MALVIN: qué facturas cancela y cómo se pagó.",
            buildCollectionAssistantPrompt(withClientOnly)
        )

        val emptyCobro = CobroDetailDto(id = 5)
        assertEquals(
            "Resumime el cobro: qué facturas cancela y cómo se pagó.",
            buildCollectionAssistantPrompt(emptyCobro)
        )
    }
}

package com.authvex.balaxysefactura.ui.screens.emission

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class EmissionNoteAndNavigationTest {

    private lateinit var repository: CfeRepository
    private lateinit var viewModel: EmissionViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `NOTE_ACCEPTS_NORMAL_TEXT - preserves normal commercial text`() {
        val input = "Nota de entrega normal"
        assertEquals(input, InvoiceNoteSanitizer.sanitizeInvoiceNote(input))
    }

    @Test
    fun `NOTE_ACCEPTS_SPANISH_ACCENTS - preserves Spanish accents`() {
        val input = "Factura con acentos: áéíóú Ü ÁÉÍÓÚ"
        assertEquals(input, InvoiceNoteSanitizer.sanitizeInvoiceNote(input))
    }

    @Test
    fun `NOTE_ACCEPTS_ENYE - preserves enye capital and lowercase`() {
        val input = "Señor Muñoz - Año 2026"
        assertEquals(input, InvoiceNoteSanitizer.sanitizeInvoiceNote(input))
    }

    @Test
    fun `NOTE_ACCEPTS_NUMBERS - preserves numbers`() {
        val input = "Pedido N° 1234567890"
        assertEquals(input, InvoiceNoteSanitizer.sanitizeInvoiceNote(input))
    }

    @Test
    fun `NOTE_ACCEPTS_PUNCTUATION - preserves punctuation and symbols`() {
        val input = "Ref: 10% dto, $100.00; (urgente) @depósito #1 + - / ="
        assertEquals(input, InvoiceNoteSanitizer.sanitizeInvoiceNote(input))
    }

    @Test
    fun `NOTE_ACCEPTS_NEWLINE_IF_SUPPORTED - preserves line breaks`() {
        val input = "Línea 1\nLínea 2"
        assertEquals(input, InvoiceNoteSanitizer.sanitizeInvoiceNote(input))
    }

    @Test
    fun `NOTE_MAX_200_ACCEPTED - accepts exactly 200 characters`() {
        val input = "A".repeat(200)
        assertEquals(200, InvoiceNoteSanitizer.sanitizeInvoiceNote(input).length)
        assertEquals(input, InvoiceNoteSanitizer.sanitizeInvoiceNote(input))
    }

    @Test
    fun `NOTE_201_TRUNCATED_TO_200 - truncates 201 characters to 200`() {
        val input = "A".repeat(201)
        val sanitized = InvoiceNoteSanitizer.sanitizeInvoiceNote(input)
        assertEquals(200, sanitized.length)
        assertEquals("A".repeat(200), sanitized)
    }

    @Test
    fun `NOTE_PASTE_500_TRUNCATED_TO_200 - pasting 500 characters truncates to 200`() {
        val input = "B".repeat(500)
        val sanitized = InvoiceNoteSanitizer.sanitizeInvoiceNote(input)
        assertEquals(200, sanitized.length)
        assertEquals("B".repeat(200), sanitized)
    }

    @Test
    fun `NOTE_REJECTS_SIMPLE_EMOJI - removes smiley face emoji`() {
        val input = "Factura válida 😀 cliente"
        val expected = "Factura válida  cliente"
        assertEquals(expected, InvoiceNoteSanitizer.sanitizeInvoiceNote(input))
    }

    @Test
    fun `NOTE_REJECTS_HEART_EMOJI - removes red heart emoji`() {
        val input = "Cliente ❤️ VIP"
        val expected = "Cliente  VIP"
        assertEquals(expected, InvoiceNoteSanitizer.sanitizeInvoiceNote(input))
    }

    @Test
    fun `NOTE_REJECTS_EMOJI_WITH_VARIATION_SELECTOR - removes sun with variation selector`() = runTest {
        val input = "Entrega ☀️ inmediata"
        val expected = "Entrega  inmediata"
        assertEquals(expected, InvoiceNoteSanitizer.sanitizeInvoiceNote(input))
    }

    @Test
    fun `NOTE_REJECTS_ZWJ_EMOJI_SEQUENCE - removes family ZWJ sequence`() = runTest {
        val input = "Familia 👨‍👩‍👧‍👦 feliz"
        val expected = "Familia  feliz"
        assertEquals(expected, InvoiceNoteSanitizer.sanitizeInvoiceNote(input))
    }

    @Test
    fun `NOTE_TEXT_AROUND_EMOJI_IS_PRESERVED - preserves text before and after rocket emoji`() = runTest {
        val input = "Entrega urgente 🚀 mañana"
        val expected = "Entrega urgente  mañana"
        assertEquals(expected, InvoiceNoteSanitizer.sanitizeInvoiceNote(input))
    }

    @Test
    fun `NOTE_SUBMIT_DTO_MAX_200 and NOTE_SUBMIT_DTO_HAS_NO_EMOJI - DTO submission sanitizes note to 200 chars without emojis`() = runTest {
        val pos = PuntoVentaDto(1, "Caja 1", 1, true, false)
        val type = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")

        whenever(repository.getPuntosVenta()).thenReturn(Result.success(listOf(pos)))
        whenever(repository.getDocumentosHabilitados(1)).thenReturn(Result.success(listOf(CfeFiscalDocumentAvailabilityGroupDto(1, listOf(type)))))
        whenever(repository.getClientes(anyOrNull())).thenReturn(Result.success(listOf(ClienteDto(10, "ABITAB"))))
        whenever(repository.getProductos(anyOrNull())).thenReturn(Result.success(listOf(ProductoDto(50, "Prod", precio = 100.0))))
        whenever(repository.getTasaCambios(any())).thenReturn(Result.success(listOf(TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0))))
        whenever(repository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(2, "Almacén Central"))))
        whenever(repository.caePrecheck(any(), any(), anyOrNull(), any())).thenReturn(Result.success(CaePrecheckResultDto(hasValidCae = true, message = "OK")))

        viewModel = EmissionViewModel(repository)
        viewModel.selectFiscalType(type)

        // Input 250 chars containing rockets 🚀
        val longEmojiInput = "Nota importante 🚀 " + "X".repeat(250)
        viewModel.onNotasChanged(longEmojiInput)

        assertTrue(viewModel.notas.length <= 200)
        assertFalse(viewModel.notas.contains("🚀"))

        whenever(repository.createFacturaElectronicDraft(check { request ->
            assertTrue((request.nota ?: "").length <= 200)
            assertFalse((request.nota ?: "").contains("🚀"))
        })).thenReturn(Result.success(999L))

        viewModel.proceedToEmission()
    }

    @Test
    fun `EMIT_CFE_BACK_RETURNS_HOME - back when in SelectType or SelectPOS triggers back navigation`() = runTest {
        val pos = PuntoVentaDto(1, "Caja 1", 1, true, false)
        val type = CfeFiscalDocumentAvailabilityItemDto(111, "e-Factura", true, null, 1, "A")

        whenever(repository.getPuntosVenta()).thenReturn(Result.success(listOf(pos)))
        whenever(repository.getDocumentosHabilitados(1)).thenReturn(Result.success(listOf(CfeFiscalDocumentAvailabilityGroupDto(1, listOf(type)))))

        viewModel = EmissionViewModel(repository)

        assertTrue(viewModel.uiState is EmissionUiState.SelectType)

        // Calling resetToTypeSelection resets state back to SelectType safely
        viewModel.resetToTypeSelection()
        assertTrue(viewModel.uiState is EmissionUiState.SelectType)
    }
}

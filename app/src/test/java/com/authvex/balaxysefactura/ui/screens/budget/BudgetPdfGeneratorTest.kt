package com.authvex.balaxysefactura.ui.screens.budget

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.ui.screens.budget.detail.BudgetPdfPresentationBuilder
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BudgetPdfGeneratorTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `PDF_LINE_PRICE_HAS_NO_CURRENCY_PREFIX and PDF_LINE_IVA_HAS_NO_CURRENCY_PREFIX and PDF_LINE_AMOUNT_HAS_NO_CURRENCY_PREFIX`() {
        val company = EmpresaDto(id = 10, nombre = "Empresa Test", moneda = CatalogoItemDto(50, "Pesos Test", "UYU"))
        val budget = BudgetDto(
            id = 105L,
            folio = "PF-4/01/2026",
            fechaEmision = "2026-01-04",
            fechaVencimiento = "2026-02-04",
            importeBase = 12550.0,
            iva = 0.0,
            importeTotalBase = 13030.0,
            moneda = CatalogoItemDto(50, "Pesos Test", "UYU"),
            documentoProductos = listOf(
                BudgetDocumentProductDto(
                    id = 1001L,
                    precioBase = 12550.0,
                    importeBase = 12550.0,
                    iva = 0.0,
                    porcentajeIva = 0.0,
                    producto = ProductoDto(50, "Detergente...", precio = 15000.0, tasaIva = 0.0)
                )
            )
        )

        val model = BudgetPdfPresentationBuilder.build(budget, company)

        // Line item fields MUST NOT contain currency code/symbol
        assertEquals("12550.00", model.lines.first().precioText)
        assertEquals("0.00", model.lines.first().ivaText)
        assertEquals("12550.00", model.lines.first().importeText)

        // Totals MUST KEEP currency code/symbol
        assertEquals("UYU 12550.00", model.subtotalNetoText)
        assertEquals("UYU 13030.00", model.importeTotalText)
    }

    @Test
    fun `PDF_DOES_NOT_USE_PRODUCT_CATALOG_PRICE - uses persisted document price 1029 not current catalog price 1500`() {
        val company = EmpresaDto(id = 10, nombre = "Empresa Test", moneda = CatalogoItemDto(50, "Pesos Test", "UYU"))
        val budget = BudgetDto(
            id = 105L,
            folio = "PF-4/01/2026",
            fechaEmision = "2026-01-04",
            fechaVencimiento = "2026-02-04",
            importeBase = 1029.0,
            iva = 0.0,
            importeTotalBase = 1029.0,
            moneda = CatalogoItemDto(50, "Pesos Test", "UYU"),
            documentoProductos = listOf(
                BudgetDocumentProductDto(
                    id = 1001L,
                    precioBase = 1029.0,
                    importeBase = 1029.0,
                    iva = 0.0,
                    porcentajeIva = 0.0,
                    producto = ProductoDto(50, "Suscripción Mensual", precio = 1500.0, tasaIva = 0.0) // Catalog price 1500
                )
            )
        )

        val model = BudgetPdfPresentationBuilder.build(budget, company)

        assertEquals("1029.00", model.lines.first().precioText)
        assertEquals("UYU 1029.00", model.subtotalNetoText)
        assertEquals("UYU 1029.00", model.importeTotalText)
    }

    @Test
    fun `PDF_BASE_CURRENCY_USES_BASE_FIELDS - uses base fields for UYU base currency budget`() {
        val company = EmpresaDto(id = 10, nombre = "Empresa Test", moneda = CatalogoItemDto(50, "Pesos Test", "UYU"))
        val budget = BudgetDto(
            id = 101L,
            importeBase = 200.0,
            iva = 44.0,
            importeTotalBase = 244.0,
            ajusteRedondeoBase = 0.0,
            moneda = CatalogoItemDto(50, "Pesos Test", "UYU"),
            documentoProductos = listOf(
                BudgetDocumentProductDto(
                    id = 1001L,
                    precioBase = 100.0,
                    importeBase = 200.0,
                    iva = 44.0,
                    porcentajeIva = 0.22,
                    cantidad = 2.0
                )
            )
        )

        val model = BudgetPdfPresentationBuilder.build(budget, company)

        assertTrue(model.isBaseCurrency)
        assertEquals("100.00", model.lines.first().precioText)
        assertEquals("44.00", model.lines.first().ivaText)
        assertEquals("200.00", model.lines.first().importeText)
        assertEquals("UYU 200.00", model.subtotalNetoText)
        assertEquals("UYU 44.00", model.ivaTotalText)
        assertEquals("UYU 244.00", model.importeTotalText)
    }

    @Test
    fun `PDF_FOREIGN_LINE_PRICE_HAS_NO_CURRENCY_PREFIX - uses original fields 100 USD without currency prefix`() {
        val company = EmpresaDto(id = 10, nombre = "Empresa Test", moneda = CatalogoItemDto(50, "Pesos Test", "UYU"))
        val budget = BudgetDto(
            id = 102L,
            tasaCambio = 42.0,
            importeBase = 4200.0,
            iva = 924.0,
            importeTotalBase = 5124.0,
            importeOriginal = 100.0,
            ivaOriginal = 22.0,
            importeTotalOriginal = 122.0,
            ajusteRedondeoOriginal = 0.0,
            moneda = CatalogoItemDto(51, "Dólar", "USD"),
            documentoProductos = listOf(
                BudgetDocumentProductDto(
                    id = 1002L,
                    precioBase = 4200.0,
                    importeBase = 4200.0,
                    iva = 924.0,
                    precioOriginal = 100.0,
                    importeOriginal = 100.0,
                    ivaOriginal = 22.0,
                    porcentajeIva = 0.22,
                    cantidad = 1.0
                )
            )
        )

        val model = BudgetPdfPresentationBuilder.build(budget, company)

        assertFalse(model.isBaseCurrency)
        assertEquals("100.00", model.lines.first().precioText)
        assertEquals("22.00", model.lines.first().ivaText)
        assertEquals("100.00", model.lines.first().importeText)
        assertEquals("USD 100.00", model.subtotalNetoText)
        assertEquals("USD 22.00", model.ivaTotalText)
        assertEquals("USD 122.00", model.importeTotalText)
    }

    @Test
    fun `PDF_MULTIPLE_TAX_RATES - correctly breaks down 0 percent, 10 percent, and 22 percent tax rates`() {
        val company = EmpresaDto(id = 10, nombre = "Empresa Test", moneda = CatalogoItemDto(50, "Pesos Test", "UYU"))
        val budget = BudgetDto(
            id = 103L,
            importeBase = 300.0,
            iva = 32.0, // 0 + 10 + 22
            importeTotalBase = 332.0,
            moneda = CatalogoItemDto(50, "Pesos Test", "UYU"),
            documentoProductos = listOf(
                BudgetDocumentProductDto(id = 1L, precioBase = 100.0, importeBase = 100.0, iva = 0.0, porcentajeIva = 0.0),
                BudgetDocumentProductDto(id = 2L, precioBase = 100.0, importeBase = 100.0, iva = 10.0, porcentajeIva = 0.10),
                BudgetDocumentProductDto(id = 3L, precioBase = 100.0, importeBase = 100.0, iva = 22.0, porcentajeIva = 0.22)
            )
        )

        val model = BudgetPdfPresentationBuilder.build(budget, company)

        assertEquals(3, model.ivaBreakdown.size)
        val breakdown0 = model.ivaBreakdown.find { it.label == "IVA 0%" }
        val breakdown10 = model.ivaBreakdown.find { it.label == "IVA 10%" }
        val breakdown22 = model.ivaBreakdown.find { it.label == "IVA 22%" }

        assertNotNull(breakdown0)
        assertNotNull(breakdown10)
        assertNotNull(breakdown22)

        assertEquals("UYU 0.00", breakdown0?.amountText)
        assertEquals("UYU 10.00", breakdown10?.amountText)
        assertEquals("UYU 22.00", breakdown22?.amountText)
    }

    @Test
    fun `PDF_DISCOUNT_PRESENTATION - detects line and global discounts`() {
        val company = EmpresaDto(id = 10, nombre = "Empresa Test", moneda = CatalogoItemDto(50, "Pesos Test", "UYU"))
        val budget = BudgetDto(
            id = 104L,
            importeBase = 90.0,
            descuento = 10.0, // Global discount 10
            iva = 19.8,
            importeTotalBase = 109.8,
            moneda = CatalogoItemDto(50, "Pesos Test", "UYU"),
            documentoProductos = listOf(
                BudgetDocumentProductDto(
                    id = 1L,
                    cantidad = 1.0,
                    precioBase = 100.0,
                    descuento = 10.0, // Line discount 10
                    importeBase = 90.0,
                    iva = 19.8,
                    porcentajeIva = 0.22
                )
            )
        )

        val model = BudgetPdfPresentationBuilder.build(budget, company)

        assertTrue(model.showDiscountColumn)
        assertEquals("10.0%", model.lines.first().descuentoPercentText)
        assertEquals("UYU 110.00", model.subtotalBrutoText)
        assertEquals("UYU 10.00", model.descuentoLineasText)
        assertEquals("UYU 10.00", model.descuentoGlobalText)
        assertEquals("UYU 90.00", model.subtotalNetoText)
    }

    @Test
    fun `COMPANY_JSON_MAPPING_TEST - deserializes EmpresaDto JSON with nombre, logo, and contacto`() {
        val rawJson = """
            {
              "id": 10,
              "nombre": "Empresa Balaxys S.A.",
              "logo": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
              "contacto": {
                "direccion": "Av. 18 de Julio 1234",
                "telefono": "+598 99 123 456",
                "email": "contacto@balaxys.uy"
              },
              "moneda": {
                "id": 50,
                "denominacion": "Pesos Uruguayos",
                "codigo": "UYU"
              }
            }
        """.trimIndent()

        val parsed = json.decodeFromString<EmpresaDto>(rawJson)

        assertEquals(10, parsed.id)
        assertEquals("Empresa Balaxys S.A.", parsed.nombre)
        assertTrue(parsed.logo!!.startsWith("data:image/png;base64"))
        assertEquals("Av. 18 de Julio 1234", parsed.contacto?.direccion)
        assertEquals("+598 99 123 456", parsed.contacto?.telefono)
        assertEquals("contacto@balaxys.uy", parsed.contacto?.email)
        assertEquals(50, parsed.moneda?.id)
    }

    @Test
    fun `PDF_TERMS_AND_NOTE - includes terms and notes if non-blank`() {
        val company = EmpresaDto(id = 10, nombre = "Empresa Test")
        val budget = BudgetDto(
            id = 106L,
            terminoCondiciones = "Pago 30 días",
            nota = "Entrega en depósito central",
            moneda = CatalogoItemDto(50, "Pesos Test", "UYU")
        )

        val model = BudgetPdfPresentationBuilder.build(budget, company)

        assertEquals("Pago 30 días", model.terminoCondicionesText)
        assertEquals("Entrega en depósito central", model.notaText)
    }
}

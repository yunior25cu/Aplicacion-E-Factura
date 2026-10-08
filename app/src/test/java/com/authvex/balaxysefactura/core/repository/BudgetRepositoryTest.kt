package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.BudgetApi
import com.authvex.balaxysefactura.core.network.BudgetCreateDto
import com.authvex.balaxysefactura.core.network.BudgetDocumentProductCreateDto
import com.authvex.balaxysefactura.core.network.BudgetFacturarDto
import com.authvex.balaxysefactura.core.network.BudgetFacturarLineaDto
import com.authvex.balaxysefactura.core.network.BudgetInvoiceOptionsDto
import com.authvex.balaxysefactura.core.network.BudgetPuntoVentaDto
import com.authvex.balaxysefactura.core.network.CfeApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BudgetRepositoryTest {

    private lateinit var budgetApi: BudgetApi
    private lateinit var cfeApi: CfeApi
    private lateinit var repository: BudgetRepository

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        budgetApi = mock()
        cfeApi = mock()
        repository = BudgetRepository(budgetApi)
    }

    @Test
    fun `getInvoiceOptions calls correct GET endpoint`() = runTest {
        val expected = BudgetInvoiceOptionsDto(
            esElectronico = true,
            cfeCode = 111,
            tipoFiscal = "e-Factura",
            puntosVenta = listOf(BudgetPuntoVentaDto(id = 1, nombre = "PV Principal", numero = 1, esPredeterminado = true))
        )
        whenever(budgetApi.getInvoiceOptions(105)).thenReturn(expected)

        val result = repository.getInvoiceOptions(105)

        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull()?.esElectronico)
        assertEquals(111, result.getOrNull()?.cfeCode)
        assertEquals("e-Factura", result.getOrNull()?.tipoFiscal)
        assertEquals(1, result.getOrNull()?.puntosVenta?.size)
        verify(budgetApi).getInvoiceOptions(105)
    }

    @Test
    fun `invoiceOptions DTO camelCase JSON parsing succeeds`() {
        val rawJson = """
            {
              "esElectronico": true,
              "cfeCode": 101,
              "tipoFiscal": "e-Ticket",
              "puntosVenta": [
                {
                  "id": 10,
                  "nombre": "Caja 1",
                  "numero": 1,
                  "esPredeterminado": true
                }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<BudgetInvoiceOptionsDto>(rawJson)

        assertTrue(parsed.esElectronico)
        assertEquals(101, parsed.cfeCode)
        assertEquals("e-Ticket", parsed.tipoFiscal)
        assertEquals(1, parsed.puntosVenta.size)
        assertEquals("Caja 1", parsed.puntosVenta.first().nombre)
        assertTrue(parsed.puntosVenta.first().esPredeterminado)
    }

    @Test
    fun `createBudget serializes PreFacturaCreateDto contract correctly`() = runTest {
        whenever(budgetApi.createBudget(any())).thenReturn(501L)

        val createDto = BudgetCreateDto(
            fechaEmision = "2026-10-08",
            fechaConfirmacion = "2026-10-08",
            fechaVencimiento = "2026-11-08",
            numeroReferencia = "REF-123",
            nota = "Nota de prueba",
            terminoCondiciones = "Contado",
            esElectronico = false,
            idMoneda = 1,
            tasaCambio = 1.0,
            importeBase = 100.0,
            iva = 22.0,
            descuento = 0.0,
            importeTotalBase = 122.0,
            importeOriginal = 100.0,
            ivaOriginal = 22.0,
            descuentoOriginal = 0.0,
            importeTotalOriginal = 122.0,
            idAlmacen = 2,
            idCliente = 10,
            idCentroCosto = null,
            documentoProductos = listOf(
                BudgetDocumentProductCreateDto(
                    idProducto = 50,
                    cantidad = 2.0,
                    precioBase = 50.0,
                    importeBase = 100.0,
                    iva = 22.0,
                    descuento = 0.0,
                    ivaOriginal = 22.0,
                    descuentoOriginal = 0.0,
                    precioBaseConIva = 61.0,
                    importeBaseConIva = 122.0,
                    precioOriginal = 50.0,
                    importeOriginal = 100.0,
                    precioOriginalConIva = 61.0,
                    importeOriginalConIva = 122.0
                )
            )
        )

        val jsonString = json.encodeToString(createDto)

        assertTrue("Missing fechaEmision", jsonString.contains("fechaEmision"))
        assertTrue("Missing importeTotalBase", jsonString.contains("importeTotalBase"))
        assertTrue("Missing documentoProductos", jsonString.contains("documentoProductos"))
        assertTrue("Missing importeBaseConIva", jsonString.contains("importeBaseConIva"))

        val result = repository.createBudget(createDto)
        assertEquals(501L, result.getOrNull())
    }

    @Test
    fun `invoiceBudget payload excludes tasaCambio, cfeCode, and serie`() = runTest {
        val invoiceDto = BudgetFacturarDto(
            fechaEmision = "2026-10-08",
            fechaConfirmacion = "2026-10-08",
            puntoVentaIdFiscal = 5,
            lineas = listOf(BudgetFacturarLineaDto(idDocumentoProducto = 1001, cantidad = 1.0))
        )

        val serialized = json.encodeToString(invoiceDto)

        assertFalse(serialized.contains("tasaCambio"))
        assertFalse(serialized.contains("cfeCode"))
        assertFalse(serialized.contains("serie"))
        assertFalse(serialized.contains("esElectronico"))

        whenever(budgetApi.invoiceBudget(eq(105L), anyOrNull())).thenReturn(999L)

        val result = repository.invoiceBudget(105L, invoiceDto)

        assertTrue(result.isSuccess)
        assertEquals(999L, result.getOrNull())
        verify(budgetApi).invoiceBudget(eq(105L), eq(invoiceDto))
    }

    @Test
    fun `critical architecture test - invoicing budget calls 0 electronic draft or validate or emit APIs`() = runTest {
        whenever(budgetApi.invoiceBudget(eq(105L), anyOrNull())).thenReturn(888L)

        val result = repository.invoiceBudget(105L)

        assertTrue(result.isSuccess)
        assertEquals(888L, result.getOrNull())

        // Confirm 0 calls to CFE emission endpoints
        verify(cfeApi, never()).createFacturaElectronicDraft(any())
        verify(cfeApi, never()).validateCfe(any(), any(), any(), any())
        verify(cfeApi, never()).emitCfe(any(), any())
    }

    @Test
    fun `full invoice omits lineas payload`() {
        val fullInvoiceDto = BudgetFacturarDto(
            fechaEmision = "2026-10-08",
            fechaConfirmacion = "2026-10-08",
            puntoVentaIdFiscal = 1,
            lineas = null
        )

        val serialized = json.encodeToString(fullInvoiceDto)

        assertFalse(serialized.contains("lineas"))
    }
}

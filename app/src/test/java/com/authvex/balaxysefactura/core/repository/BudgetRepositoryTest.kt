package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.BudgetApi
import com.authvex.balaxysefactura.core.network.BudgetCreateDto
import com.authvex.balaxysefactura.core.network.BudgetDocumentProductCreateDto
import com.authvex.balaxysefactura.core.network.BudgetDto
import com.authvex.balaxysefactura.core.network.BudgetFacturarDto
import com.authvex.balaxysefactura.core.network.BudgetFacturarLineaDto
import com.authvex.balaxysefactura.core.network.BudgetInvoiceOptionsDto
import com.authvex.balaxysefactura.core.network.BudgetListResponse
import com.authvex.balaxysefactura.core.network.BudgetPuntoVentaDto
import com.authvex.balaxysefactura.core.network.CfeApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class BudgetRepositoryTest {

    private lateinit var fakeBudgetApi: FakeBudgetApi
    private lateinit var cfeApi: CfeApi
    private lateinit var repository: BudgetRepository

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    class FakeBudgetApi : BudgetApi {
        var createResultId: Long = 501L
        var invoiceResultId: Long = 999L
        var optionsResult: BudgetInvoiceOptionsDto = BudgetInvoiceOptionsDto()
        var lastCreatedDto: BudgetCreateDto? = null
        var lastInvoicedDto: BudgetFacturarDto? = null
        var lastInvoicedId: Long? = null

        override suspend fun getBudgets(filter: Map<String, String>): BudgetListResponse = BudgetListResponse()
        override suspend fun getBudgetById(id: Long): BudgetDto = BudgetDto(id = id)
        override suspend fun createBudget(dto: BudgetCreateDto): Long {
            lastCreatedDto = dto
            return createResultId
        }
        override suspend fun updateBudget(dto: BudgetCreateDto) {}
        override suspend fun confirmBudget(id: Long) {}
        override suspend fun cancelBudget(id: Long) {}
        override suspend fun getInvoiceOptions(id: Long): BudgetInvoiceOptionsDto = optionsResult
        override suspend fun invoiceBudget(id: Long, dto: BudgetFacturarDto): Long {
            lastInvoicedId = id
            lastInvoicedDto = dto
            return invoiceResultId
        }
    }

    @Before
    fun setUp() {
        fakeBudgetApi = FakeBudgetApi()
        cfeApi = mock()
        repository = BudgetRepository(fakeBudgetApi)
    }

    @Test
    fun `getInvoiceOptions calls correct GET endpoint`() = runTest {
        val expected = BudgetInvoiceOptionsDto(
            esElectronico = true,
            cfeCode = 111,
            tipoFiscal = "e-Factura",
            puntosVenta = listOf(BudgetPuntoVentaDto(id = 1, nombre = "PV Principal", numero = 1, esPredeterminado = true))
        )
        fakeBudgetApi.optionsResult = expected

        val result = repository.getInvoiceOptions(105)

        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull()?.esElectronico)
        assertEquals(111, result.getOrNull()?.cfeCode)
        assertEquals("e-Factura", result.getOrNull()?.tipoFiscal)
        assertEquals(1, result.getOrNull()?.puntosVenta?.size)
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
    fun `FULL_INVOICE_BODY_NOT_NULL and FULL_INVOICE_LINES_NULL - full invoice passes non-null dto with null lineas`() = runTest {
        val fullInvoiceDto = BudgetFacturarDto(
            fechaEmision = "2026-10-08",
            fechaConfirmacion = "2026-10-08",
            puntoVentaIdFiscal = 3,
            lineas = null
        )

        val jsonString = json.encodeToString(fullInvoiceDto)

        assertTrue(jsonString.contains("fechaEmision"))
        assertTrue(jsonString.contains("fechaConfirmacion"))
        assertTrue(jsonString.contains("puntoVentaIdFiscal"))
        assertNull(fullInvoiceDto.lineas)

        fakeBudgetApi.invoiceResultId = 999L

        val result = repository.invoiceBudget(105L, fullInvoiceDto)

        assertTrue(result.isSuccess)
        assertEquals(999L, result.getOrNull())
        assertEquals(105L, fakeBudgetApi.lastInvoicedId)
        assertEquals(fullInvoiceDto, fakeBudgetApi.lastInvoicedDto)
    }

    @Test
    fun `PARTIAL_INVOICE_BODY_NOT_NULL - partial invoice passes dto with selected lineas`() = runTest {
        val partialInvoiceDto = BudgetFacturarDto(
            fechaEmision = "2026-10-08",
            fechaConfirmacion = "2026-10-08",
            puntoVentaIdFiscal = 3,
            lineas = listOf(BudgetFacturarLineaDto(idDocumentoProducto = 500, cantidad = 2.5))
        )

        val jsonString = json.encodeToString(partialInvoiceDto)

        assertTrue(jsonString.contains("lineas"))
        assertTrue(jsonString.contains("idDocumentoProducto"))
        assertEquals(1, partialInvoiceDto.lineas?.size)

        fakeBudgetApi.invoiceResultId = 1001L

        val result = repository.invoiceBudget(105L, partialInvoiceDto)

        assertTrue(result.isSuccess)
        assertEquals(1001L, result.getOrNull())
        assertEquals(partialInvoiceDto, fakeBudgetApi.lastInvoicedDto)
    }

    @Test
    fun `createBudget serializes PreFacturaCreateDto contract correctly`() = runTest {
        val createDto = BudgetCreateDto(
            fechaEmision = "2026-10-08",
            fechaConfirmacion = "2026-10-08",
            fechaVencimiento = "2026-11-08",
            numeroReferencia = "REF-123",
            nota = "Nota de prueba",
            terminoCondiciones = "Contado",
            preciosIncluyenIva = true,
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

        fakeBudgetApi.createResultId = 501L

        val jsonString = json.encodeToString(createDto)

        assertTrue(jsonString.contains("fechaEmision"))
        assertTrue(jsonString.contains("documentoProductos"))
        assertTrue(jsonString.contains("importeBaseConIva"))

        val result = repository.createBudget(createDto)
        assertTrue(result.isSuccess)
        assertEquals(501L, result.getOrNull())
        assertEquals(createDto, fakeBudgetApi.lastCreatedDto)
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

        fakeBudgetApi.invoiceResultId = 999L

        val result = repository.invoiceBudget(105L, invoiceDto)

        assertTrue(result.isSuccess)
        assertEquals(999L, result.getOrNull())
        assertEquals(invoiceDto, fakeBudgetApi.lastInvoicedDto)
    }

    @Test
    fun `critical architecture test - invoicing budget calls 0 electronic draft or validate or emit APIs`() = runTest {
        val invoiceDto = BudgetFacturarDto(
            fechaEmision = "2026-10-08",
            fechaConfirmacion = "2026-10-08",
            puntoVentaIdFiscal = 1
        )
        fakeBudgetApi.invoiceResultId = 888L

        val result = repository.invoiceBudget(105L, invoiceDto)

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

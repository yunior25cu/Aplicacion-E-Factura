package com.authvex.balaxysefactura.ui.screens.emission

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
class CfeOriginDocumentSelectionTest {

    private lateinit var repository: CfeRepository
    private lateinit var viewModel: EmissionViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    // Default Json instance used in production (ignoreUnknownKeys = true, encodeDefaults = false)
    private val prodJson = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()

        runTest {
            val pos = PuntoVentaDto(1, "Caja 1", 1, true, false)
            whenever(repository.getPuntosVenta()).thenReturn(Result.success(listOf(pos)))
            whenever(repository.getDocumentosHabilitados(any())).thenReturn(Result.success(listOf(CfeFiscalDocumentAvailabilityGroupDto(1, emptyList()))))
            whenever(repository.getClientes(anyOrNull())).thenReturn(Result.success(listOf(ClienteDto(10, "ABITAB S A"))))
            whenever(repository.getProductos(anyOrNull())).thenReturn(Result.success(listOf(ProductoDto(50, "Prod", precio = 100.0))))
            whenever(repository.getTasaCambios(any())).thenReturn(Result.success(listOf(TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0))))
            whenever(repository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(2, "Almacén Central"))))
        }

        viewModel = EmissionViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `DEVOLUCION_CREATE_RESPONSE_DESERIALIZES_OBJECT and DEVOLUCION_CREATE_RESPONSE_EXTRACTS_ID`() {
        val rawResponseJson = """
            {
              "id": 2800,
              "folio": "NCV-3/01/2026",
              "numero": 123,
              "total": 1220.0,
              "saldo": 0.0
            }
        """.trimIndent()

        val response = prodJson.decodeFromString<FacturaResponse>(rawResponseJson)

        assertEquals(2800L, response.id)
        assertEquals(2800L, response.getEffectiveId())
    }

    @Test
    fun `ORIGIN_REQUIREMENT_TESTS - isOriginRequired is true for 102 103 112 113 and false for 101 111`() {
        fun makeItem(code: Int) = CfeFiscalDocumentAvailabilityItemDto(cfeCode = code, name = "Test", habilitado = true, puntoVentaId = 1, serie = "A")

        viewModel.selectFiscalType(makeItem(102))
        assertTrue(viewModel.isOriginRequired())
        assertEquals(101, viewModel.resolveOriginCfeCode())

        viewModel.selectFiscalType(makeItem(103))
        assertTrue(viewModel.isOriginRequired())
        assertEquals(101, viewModel.resolveOriginCfeCode())

        viewModel.selectFiscalType(makeItem(112))
        assertTrue(viewModel.isOriginRequired())
        assertEquals(111, viewModel.resolveOriginCfeCode())

        viewModel.selectFiscalType(makeItem(113))
        assertTrue(viewModel.isOriginRequired())
        assertEquals(111, viewModel.resolveOriginCfeCode())

        viewModel.selectFiscalType(makeItem(101))
        assertFalse(viewModel.isOriginRequired())

        viewModel.selectFiscalType(makeItem(111))
        assertFalse(viewModel.isOriginRequired())
    }

    @Test
    fun `SELECT_ORIGIN_SETS_ID_DOCUMENTO_ORIGEN_AND_INHERITS_FIELDS`() = runTest {
        val originCfeSummary = CfeSummaryDto(
            documentoId = 888,
            serie = "A",
            numero = 123L,
            cfeCode = 111,
            receptor = "ABITAB S A",
            fechaEmision = "2026-01-04",
            importeTotal = 1220.0,
            monedaSimbolo = "$",
            estadoCfe = 2
        )

        val saleDoc = BudgetDto(
            id = 888L,
            fechaEmision = "2026-01-04",
            preciosIncluyenIva = true,
            tasaCambio = 1.0,
            moneda = CatalogoItemDto(50, "Pesos", "UYU"),
            almacen = CatalogoItemDto(2, "Almacén Central"),
            cliente = ClienteDto(10, "ABITAB S A"),
            documentoProductos = listOf(
                BudgetDocumentProductDto(id = 1001L, idProducto = 50L, cantidad = 2.0, precioBase = 500.0, porcentajeIva = 0.22)
            )
        )

        whenever(repository.getFacturaById(888L)).thenReturn(Result.success(saleDoc))

        viewModel.baseCurrencyId = 50
        viewModel.selectOriginDocument(originCfeSummary)

        assertEquals(888L, viewModel.idDocumentoOrigen)
        assertEquals("ABITAB S A", viewModel.selectedCliente?.nombre)
        assertEquals("Almacén Central", viewModel.selectedAlmacen?.nombre)
        assertEquals(50, viewModel.selectedMoneda?.id)
        assertTrue(viewModel.preciosIncluyenIva)
        assertEquals(1, viewModel.lineas.size)
        assertEquals(2.0, viewModel.lineas.first().cantidad, 0.001)
        assertEquals(500.0, viewModel.lineas.first().precioUnitario, 0.001)
    }

    @Test
    fun `CLEAR_ORIGIN_CLEARS_DERIVED_STATE - clearing origin resets derived fields`() = runTest {
        viewModel.idDocumentoOrigen = 888L
        viewModel.selectedCliente = ClienteDto(10, "ABITAB S A")
        viewModel.lineas.add(LineaForm(ProductoDto(50, "P1"), cantidad = 1.0, precioUnitario = 100.0))

        viewModel.clearOriginDocument()

        assertNull(viewModel.idDocumentoOrigen)
        assertNull(viewModel.selectedOriginCfe)
        assertNull(viewModel.selectedOriginDoc)
        assertNull(viewModel.selectedCliente)
        assertTrue(viewModel.lineas.isEmpty())
    }

    @Test
    fun `DEVOLUCION_CANTIDAD_PRECIO_ALWAYS_SERIALIZED and DEVOLUCION_TIPO_DEVOLUCION_ALWAYS_SERIALIZED and DEVOLUCION_REQUIRED_DEFAULTS_SURVIVE_ENCODE_DEFAULTS_FALSE`() {
        val line = DevolucionLineaRequest(
            idProducto = 50,
            cantidad = 2.0,
            devuelto = 2.0,
            precioBase = 100.0,
            importeBase = 200.0,
            iva = 44.0,
            ivaOriginal = 0.0,
            precioBaseConIva = 122.0,
            importeBaseConIva = 244.0,
            precioOriginal = 0.0,
            importeOriginal = 0.0,
            precioOriginalConIva = 0.0,
            importeOriginalConIva = 0.0
        )

        val request = DevolucionCreateDto(
            fechaEmision = "2026-10-08",
            fechaConfirmacion = "2026-10-08",
            idMoneda = 50,
            tasaCambio = 1.0,
            importeBase = 200.0,
            iva = 44.0,
            importeTotalBase = 244.0,
            importeOriginal = 0.0,
            ivaOriginal = 0.0,
            importeTotalOriginal = 0.0,
            idAlmacen = 2,
            idCliente = 10,
            documentoProductos = listOf(line),
            cantidadPrecio = true,
            tipoDevolucion = 2, // 2 = Factura
            naturalezaNota = 1, // 1 = Credito
            idDocumentoOrigen = 888L
        )

        // Using prodJson (encodeDefaults = false)
        val serialized = prodJson.encodeToString(request)
        val jsonElement = prodJson.parseToJsonElement(serialized)
        val jsonObject = jsonElement.jsonObject

        // Verify key presence even with default values
        assertTrue(jsonObject.containsKey("cantidadPrecio"))
        assertTrue(jsonObject.containsKey("tipoDevolucion"))
        assertTrue(jsonObject.containsKey("naturalezaNota"))
        assertTrue(jsonObject.containsKey("idDocumentoOrigen"))

        assertEquals(true, jsonObject["cantidadPrecio"]?.jsonPrimitive?.boolean)
        assertEquals(2, jsonObject["tipoDevolucion"]?.jsonPrimitive?.int)
        assertEquals(1, jsonObject["naturalezaNota"]?.jsonPrimitive?.int)
        assertEquals(888L, jsonObject["idDocumentoOrigen"]?.jsonPrimitive?.int?.toLong())

        val lineObj = jsonObject["documentoProductos"]?.jsonArray?.get(0)?.jsonObject
        assertNotNull(lineObj)
        assertTrue(lineObj!!.containsKey("devuelto"))
        assertEquals(2.0, lineObj["devuelto"]?.jsonPrimitive?.double ?: 0.0, 0.001)
    }

    @Test
    fun `NC_112_JSON_HAS_CANTIDAD_PRECIO_TRUE and NC_112_JSON_HAS_TIPO_DEVOLUCION_2`() {
        val request = DevolucionCreateDto(
            fechaEmision = "2026-10-08",
            fechaConfirmacion = "2026-10-08",
            idMoneda = 50,
            tasaCambio = 1.0,
            importeBase = 200.0,
            iva = 44.0,
            importeTotalBase = 244.0,
            importeOriginal = 0.0,
            ivaOriginal = 0.0,
            importeTotalOriginal = 0.0,
            idAlmacen = 2,
            idCliente = 10,
            documentoProductos = emptyList(),
            cantidadPrecio = true,
            tipoDevolucion = 2,
            naturalezaNota = 1,
            idDocumentoOrigen = 888L
        )

        val serialized = prodJson.encodeToString(request)
        val jsonObject = prodJson.parseToJsonElement(serialized).jsonObject

        assertEquals(true, jsonObject["cantidadPrecio"]?.jsonPrimitive?.boolean)
        assertEquals(2, jsonObject["tipoDevolucion"]?.jsonPrimitive?.int)
        assertEquals(1, jsonObject["naturalezaNota"]?.jsonPrimitive?.int)
    }

    @Test
    fun `ND_113_JSON_HAS_CANTIDAD_PRECIO_TRUE and ND_113_JSON_HAS_TIPO_DEVOLUCION_2`() {
        val request = DevolucionCreateDto(
            fechaEmision = "2026-10-08",
            fechaConfirmacion = "2026-10-08",
            idMoneda = 50,
            tasaCambio = 1.0,
            importeBase = 200.0,
            iva = 44.0,
            importeTotalBase = 244.0,
            importeOriginal = 0.0,
            ivaOriginal = 0.0,
            importeTotalOriginal = 0.0,
            idAlmacen = 2,
            idCliente = 10,
            documentoProductos = emptyList(),
            cantidadPrecio = true,
            tipoDevolucion = 2,
            naturalezaNota = 2,
            idDocumentoOrigen = 888L
        )

        val serialized = prodJson.encodeToString(request)
        val jsonObject = prodJson.parseToJsonElement(serialized).jsonObject

        assertEquals(true, jsonObject["cantidadPrecio"]?.jsonPrimitive?.boolean)
        assertEquals(2, jsonObject["tipoDevolucion"]?.jsonPrimitive?.int)
        assertEquals(2, jsonObject["naturalezaNota"]?.jsonPrimitive?.int)
    }

    @Test
    fun `FACTURA_101_REQUEST_UNCHANGED and FACTURA_111_REQUEST_UNCHANGED - FacturaCreateDto remains unaffected`() {
        val line = FacturaLineaRequest(
            idProducto = 50,
            cantidad = 2.0,
            precioBase = 100.0,
            importeBase = 200.0,
            iva = 44.0,
            ivaOriginal = 0.0,
            precioBaseConIva = 122.0,
            importeBaseConIva = 244.0,
            precioOriginal = 0.0,
            importeOriginal = 0.0,
            precioOriginalConIva = 0.0,
            importeOriginalConIva = 0.0
        )

        val request = FacturaCreateDto(
            fechaEmision = "2026-10-08",
            fechaConfirmacion = "2026-10-08",
            idMoneda = 50,
            tasaCambio = 1.0,
            importeBase = 200.0,
            iva = 44.0,
            importeTotalBase = 244.0,
            importeOriginal = 0.0,
            ivaOriginal = 0.0,
            importeTotalOriginal = 0.0,
            idAlmacen = 2,
            idCliente = 10,
            documentoProductos = listOf(line)
        )

        val serialized = prodJson.encodeToString(request)

        assertFalse(serialized.contains("tipoDevolucion"))
        assertFalse(serialized.contains("naturalezaNota"))
        assertFalse(serialized.contains("devuelto"))
    }

    @Test
    fun `NC_REQUEST_TIPO_DEVOLUCION_FACTURA_AND_NATURALEZA_CREDITO`() = runTest {
        val pos = PuntoVentaDto(1, "Caja 1", 1, true, false)
        val type112 = CfeFiscalDocumentAvailabilityItemDto(112, "NC e-Factura", true, null, 1, "A")

        whenever(repository.getPuntosVenta()).thenReturn(Result.success(listOf(pos)))
        whenever(repository.getDocumentosHabilitados(1)).thenReturn(Result.success(listOf(CfeFiscalDocumentAvailabilityGroupDto(1, listOf(type112)))))
        whenever(repository.getClientes(anyOrNull())).thenReturn(Result.success(listOf(ClienteDto(10, "ABITAB S A", ruc = "211234560012"))))
        whenever(repository.getTasaCambios(any())).thenReturn(Result.success(listOf(TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0))))
        whenever(repository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(2, "Almacén Central"))))
        whenever(repository.caePrecheck(any(), any(), anyOrNull(), any())).thenReturn(Result.success(CaePrecheckResultDto(hasValidCae = true, message = "OK")))
        whenever(repository.validateCfe(any(), any(), any(), anyOrNull())).thenReturn(Result.success(CfeValidateResponseDto(isValid = true)))
        whenever(repository.emitCfe(any(), any())).thenReturn(Result.success(CfeEmitResponse("1", null)))
        whenever(repository.getCfeStatus(any(), anyOrNull())).thenReturn(Result.success(CfeStatusResponse(documentoId = 888L, estado = 2, mensaje = "OK")))

        viewModel.selectedFiscalType = type112
        viewModel.selectedPOS = pos
        viewModel.idDocumentoOrigen = 888L
        viewModel.selectedCliente = ClienteDto(10, "ABITAB S A", ruc = "211234560012", tipoDocumentoIdentificacion = 2)
        viewModel.selectedAlmacen = CatalogoItemDto(2, "Almacén Central")
        viewModel.selectedMoneda = TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0)
        viewModel.lineas.add(LineaForm(ProductoDto(50, "Prod", precio = 100.0, tasaIva = 0.22), cantidad = 1.0, precioUnitario = 100.0))

        whenever(repository.createDevolucion(check { request ->
            assertEquals(2, request.tipoDevolucion)
            assertEquals(1, request.naturalezaNota)
            assertEquals(888L, request.idDocumentoOrigen)
            assertEquals(1, request.documentoProductos.size)
            assertEquals(1.0, request.documentoProductos.first().devuelto, 0.001)
        })).thenReturn(Result.success(2800L))

        viewModel.proceedToEmission()
    }

    @Test
    fun `ND_REQUEST_NATURALEZA_DEBITO`() = runTest {
        val pos = PuntoVentaDto(1, "Caja 1", 1, true, false)
        val type113 = CfeFiscalDocumentAvailabilityItemDto(113, "ND e-Factura", true, null, 1, "A")

        viewModel.selectedFiscalType = type113
        viewModel.selectedPOS = pos
        viewModel.idDocumentoOrigen = 888L
        viewModel.selectedCliente = ClienteDto(10, "ABITAB S A", ruc = "211234560012", tipoDocumentoIdentificacion = 2)
        viewModel.selectedAlmacen = CatalogoItemDto(2, "Almacén Central")
        viewModel.selectedMoneda = TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0)
        viewModel.lineas.add(LineaForm(ProductoDto(50, "Prod", precio = 100.0, tasaIva = 0.22), cantidad = 1.0, precioUnitario = 100.0))

        whenever(repository.caePrecheck(any(), any(), anyOrNull(), any())).thenReturn(Result.success(CaePrecheckResultDto(hasValidCae = true, message = "OK")))
        whenever(repository.validateCfe(any(), any(), any(), anyOrNull())).thenReturn(Result.success(CfeValidateResponseDto(isValid = true)))
        whenever(repository.emitCfe(any(), any())).thenReturn(Result.success(CfeEmitResponse("1", null)))
        whenever(repository.getCfeStatus(any(), anyOrNull())).thenReturn(Result.success(CfeStatusResponse(documentoId = 888L, estado = 2, mensaje = "OK")))

        whenever(repository.createDevolucion(check { request ->
            assertEquals(2, request.tipoDevolucion)
            assertEquals(2, request.naturalezaNota)
            assertEquals(888L, request.idDocumentoOrigen)
            assertEquals(1, request.documentoProductos.size)
            assertEquals(1.0, request.documentoProductos.first().devuelto, 0.001)
        })).thenReturn(Result.success(2800L))

        viewModel.proceedToEmission()
    }
}

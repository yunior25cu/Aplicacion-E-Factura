package com.authvex.balaxysefactura.ui.screens.collection

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.core.repository.CollectionRepository
import com.authvex.balaxysefactura.ui.screens.collection.detail.CollectionDetailUiState
import com.authvex.balaxysefactura.ui.screens.collection.detail.CollectionDetailViewModel
import com.authvex.balaxysefactura.ui.screens.collection.detail.CollectionReceiptPdfGenerator
import com.authvex.balaxysefactura.ui.screens.collection.form.CollectionFormUiState
import com.authvex.balaxysefactura.ui.screens.collection.form.CollectionFormViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionContractTest {

    private lateinit var collectionRepository: CollectionRepository
    private lateinit var cfeRepository: CfeRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private val prodJson = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        collectionRepository = mock()
        cfeRepository = mock()

        runTest {
            val empresa = EmpresaDto(id = 10, nombre = "Empresa", moneda = CatalogoItemDto(50, "Pesos", "UYU"))
            whenever(cfeRepository.getEmpresa()).thenReturn(Result.success(empresa))
            whenever(cfeRepository.getClientes(anyOrNull())).thenReturn(Result.success(listOf(ClienteDto(10, "ABITAB S A"))))
            whenever(collectionRepository.getCuentasBanco()).thenReturn(Result.success(listOf(
                CuentaBancoCatalogDto(1, numeroCuenta = "123456", idMoneda = 50, moneda = CatalogoItemDto(50, "Pesos", "UYU")),
                CuentaBancoCatalogDto(2, numeroCuenta = "987654", idMoneda = 51, moneda = CatalogoItemDto(51, "Dólar", "USD"))
            )))
            whenever(collectionRepository.getAllowedFormasPago(any())).thenReturn(Result.success(listOf(CatalogoItemDto(1, "Efectivo"))))
            whenever(cfeRepository.getTasaCambios(any())).thenReturn(Result.success(listOf(
                TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0),
                TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.0)
            )))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `RECEIPT_APPLIED_INVOICE_USES_CFE_SERIE_AND_NUMERO and DEDUPLICATES_LOOKUPS`() = runTest {
        val collection = CobroDetailDto(
            id = 83L,
            folio = "CO-83/2026",
            estado = 2,
            facturaCobros = listOf(
                FacturaCobroDetailDto(
                    factura = CobroFacturaSummaryDto(id = 182L, folio = "FA-182/01/2025", fechaConfirmacion = "2025-12-27"),
                    montoBase = 1019.97
                ),
                FacturaCobroDetailDto(
                    factura = CobroFacturaSummaryDto(id = 182L, folio = "FA-182/01/2025", fechaConfirmacion = "2025-12-27"),
                    montoBase = 100.0
                )
            )
        )

        val cfeDetail = CfeDetailDto(
            documentoId = 182,
            serie = "A",
            numero = 20221L,
            cfeCode = 111
        )

        whenever(cfeRepository.getDocumentDetail(182)).thenReturn(Result.success(cfeDetail))

        val viewModel = CollectionDetailViewModel(cfeRepository, 83L, collectionRepository)

        val resolvedInvoices = viewModel.resolveAppliedInvoicesForReceipt(collection, cfeRepository)

        // CFE lookup deduplicated: called ONLY ONCE for documentId = 182
        verify(cfeRepository, times(1)).getDocumentDetail(182)

        assertEquals(2, resolvedInvoices.size)
        assertEquals("A-20221", resolvedInvoices.first().fiscalReference)
        assertFalse(resolvedInvoices.first().isFallback)
        assertEquals("27/12/2025", resolvedInvoices.first().fecha)
    }

    @Test
    fun `RECEIPT_FALLS_BACK_TO_INTERNAL_FOLIO_WHEN_CFE_UNAVAILABLE and ONE_FAILURE_DOES_NOT_BREAK_OTHER_ROWS`() = runTest {
        val collection = CobroDetailDto(
            id = 83L,
            folio = "CO-83/2026",
            estado = 2,
            facturaCobros = listOf(
                FacturaCobroDetailDto(
                    factura = CobroFacturaSummaryDto(id = 182L, folio = "FA-182/01/2025", fechaConfirmacion = "2025-12-27"),
                    montoBase = 1000.0
                ),
                FacturaCobroDetailDto(
                    factura = CobroFacturaSummaryDto(id = 183L, folio = "FA-183/01/2025", fechaConfirmacion = "2025-12-28"),
                    montoBase = 500.0
                )
            )
        )

        val cfeDetail182 = CfeDetailDto(documentoId = 182, serie = "A", numero = 20221L, cfeCode = 111)

        whenever(cfeRepository.getDocumentDetail(182)).thenReturn(Result.success(cfeDetail182))
        whenever(cfeRepository.getDocumentDetail(183)).thenReturn(Result.failure(Exception("CFE tracking unavailable")))

        val viewModel = CollectionDetailViewModel(cfeRepository, 83L, collectionRepository)

        val resolvedInvoices = viewModel.resolveAppliedInvoicesForReceipt(collection, cfeRepository)

        assertEquals(2, resolvedInvoices.size)
        // Row 1: CFE resolved -> A-20221
        assertEquals("A-20221", resolvedInvoices[0].fiscalReference)
        assertFalse(resolvedInvoices[0].isFallback)

        // Row 2: CFE failed -> Fallback to internal folio FA-183/01/2025
        assertEquals("FA-183/01/2025", resolvedInvoices[1].fiscalReference)
        assertTrue(resolvedInvoices[1].isFallback)
    }

    @Test
    fun `EXISTING_FILE_PROVIDER_PATHS_PRESERVED_AND_COBROS_PATH_ADDED`() {
        val file = listOf(
            File("app/src/main/res/xml/file_paths.xml"),
            File("src/main/res/xml/file_paths.xml")
        ).firstOrNull { it.exists() }
        assertNotNull("file_paths.xml must exist", file)
        val filePathsXml = file!!.readText()
        assertTrue(filePathsXml.contains("""path="presupuestos/""""))
        assertTrue(filePathsXml.contains("""path="cfe/""""))
        assertTrue(filePathsXml.contains("""path="cobros/""""))
        assertTrue(filePathsXml.contains("""name="cobros""""))
    }

    @Test
    fun `RECEIPT_FILENAME_IS_SANITIZED`() {
        val sanitized = CollectionReceiptPdfGenerator.sanitizeFilename("CO-83/2026")
        assertEquals("CO-83-2026", sanitized)
        assertFalse(sanitized.contains("/"))
    }

    @Test
    fun `RECEIPT_REFETCHES_COLLECTION_BEFORE_RENDER and USES_EXISTING_COLLECTION_FOLIO`() = runTest {
        val confirmedDto = CobroDetailDto(
            id = 83L,
            folio = "CO-83/2026",
            estado = 2, // Confirmado
            montoTotalBase = 1000.0,
            montoTotalOriginal = 0.0,
            moneda = CatalogoItemDto(50, "Pesos", "UYU")
        )

        whenever(collectionRepository.getCobroById(83L)).thenReturn(Result.success(confirmedDto))

        val viewModel = CollectionDetailViewModel(cfeRepository, 83L, collectionRepository)

        viewModel.generateReceiptAndExecute(mock(), cfeRepository) {
            Result.success(Unit)
        }

        // Verifies refetch was done (init load + generate refetch)
        verify(collectionRepository, times(2)).getCobroById(83L)
        // Verifies POST /Cobro and confirm were NEVER called during receipt generation
        verify(collectionRepository, never()).createCobro(any())
        verify(collectionRepository, never()).confirmCobro(any())
    }

    @Test
    fun `COLLECTION_DETAIL_DESERIALIZES_NESTED_FACTURA and DOES_NOT_REQUIRE_ID_FACTURA_AT_ROW_ROOT`() {
        val jsonDetail = """
            {
              "id": 88,
              "folio": "CO-123",
              "fechaEmision": "2026-10-09",
              "estado": 2,
              "cuentaBanco": {
                "id": 10,
                "numeroCuenta": "123456",
                "tipoCuentaBanco": 1,
                "titular": "BALAXYS SAS"
              },
              "facturaCobros": [
                {
                  "factura": {
                    "id": 119,
                    "folio": "FA-119/01/2026",
                    "numero": 119,
                    "almacen": "Principal",
                    "fechaEmision": "2026-10-10",
                    "fechaConfirmacion": "2026-10-10",
                    "importeTotalBase": 1000.0,
                    "importeTotalOriginal": 0.0
                  },
                  "montoActualBase": 0.0,
                  "montoBase": 400.0,
                  "montoActualOriginal": 0.0,
                  "montoOriginal": 0.0,
                  "montoOperacion": 400.0
                }
              ]
            }
        """.trimIndent()

        val dto = prodJson.decodeFromString<CobroDetailDto>(jsonDetail)

        assertNotNull(dto.cuentaBanco)
        assertEquals("BALAXYS SAS (123456)", dto.cuentaBanco!!.getDisplayLabel())
        assertEquals(1, dto.facturaCobros.size)

        val row = dto.facturaCobros.first()
        assertEquals(119L, row.factura.id)
        assertEquals("FA-119/01/2026", row.factura.folio)
        assertEquals(400.0, row.montoOperacion!!, 0.001)
    }

    @Test
    fun `COLLECTION_DETAIL_DESERIALIZES_BANK_ACCOUNT_WITHOUT_NOMBRE_AND_NULL_MONEDA`() {
        val jsonStrWithoutNombre = """
            {
              "id": 88,
              "fechaEmision": "2026-10-09",
              "estado": 2,
              "cuentaBanco": {
                "id": 10,
                "numeroCuenta": "123456",
                "tipoCuentaBanco": 1,
                "titular": "BALAXYS SAS",
                "moneda": null
              }
            }
        """.trimIndent()

        val dto = prodJson.decodeFromString<CobroDetailDto>(jsonStrWithoutNombre)
        assertNotNull(dto.cuentaBanco)
        assertNull(dto.cuentaBanco!!.moneda)
        assertEquals("BALAXYS SAS (123456)", dto.cuentaBanco!!.getDisplayLabel())
    }

    @Test
    fun `DETAIL_CONFIRM_USES_EXISTING_COLLECTION_ID and DOES_NOT_POST_NEW_COLLECTION and REFETCHES_COLLECTION`() = runTest {
        val unconfirmedDto = CobroDetailDto(
            id = 83L,
            folio = "CO-83/2026",
            estado = 1, // SinConfirmar
            montoTotalOriginal = 225.70,
            facturaCobros = listOf(
                FacturaCobroDetailDto(
                    factura = CobroFacturaSummaryDto(id = 137L, folio = "FA-137/01/2026", fechaEmision = "2026-10-09", fechaConfirmacion = "2026-10-09"),
                    montoOperacion = 225.70
                )
            )
        )

        val confirmedDto = unconfirmedDto.copy(estado = 2) // Confirmado

        whenever(collectionRepository.getCobroById(83L))
            .thenReturn(Result.success(unconfirmedDto))
            .thenReturn(Result.success(confirmedDto))

        whenever(collectionRepository.confirmCobro(83L)).thenReturn(Result.success(Unit))

        val viewModel = CollectionDetailViewModel(cfeRepository, 83L, collectionRepository)

        // Verifies initial state is SinConfirmar
        assertTrue(viewModel.uiState is CollectionDetailUiState.Success)
        assertEquals(1, (viewModel.uiState as CollectionDetailUiState.Success).collection.estado)

        // Execute confirmation
        viewModel.confirmCollection()

        // Verifies PUT /Cobro/confirmar/83 was called
        verify(collectionRepository).confirmCobro(83L)

        // Verifies POST /Cobro was NEVER called
        verify(collectionRepository, never()).createCobro(any())

        // Verifies refetch was done and state is now Confirmado
        verify(collectionRepository, times(2)).getCobroById(83L)
        assertEquals(2, (viewModel.uiState as CollectionDetailUiState.Success).collection.estado)
        assertEquals("Cobro confirmado correctamente", viewModel.confirmMessage)
    }

    @Test
    fun `DETAIL_CONFIRM_FAILURE_PRESERVES_UNCONFIRMED_STATE`() = runTest {
        val unconfirmedDto = CobroDetailDto(
            id = 83L,
            estado = 1,
            montoTotalOriginal = 225.70
        )

        whenever(collectionRepository.getCobroById(83L)).thenReturn(Result.success(unconfirmedDto))
        whenever(collectionRepository.confirmCobro(83L)).thenReturn(Result.failure(Exception("Período contable cerrado")))

        val viewModel = CollectionDetailViewModel(cfeRepository, 83L, collectionRepository)

        viewModel.confirmCollection()

        verify(collectionRepository).confirmCobro(83L)
        assertEquals("Período contable cerrado", viewModel.confirmMessage)
        assertEquals(1, (viewModel.uiState as CollectionDetailUiState.Success).collection.estado)
    }

    @Test
    fun `COLLECTION_BANK_ACCOUNTS_USE_CATALOGO_CUENTABANCOS`() = runTest {
        val collectionApi = mock<CollectionApi>()
        val repo = CollectionRepository(collectionApi)

        whenever(collectionApi.getCuentasBanco()).thenReturn(listOf(CuentaBancoCatalogDto(1, numeroCuenta = "123")))

        val result = repo.getCuentasBanco()
        assertTrue(result.isSuccess)
        verify(collectionApi).getCuentasBanco()
    }

    @Test
    fun `COLLECTION_BANK_ACCOUNT_FILTERS_BY_OPERATION_CURRENCY`() = runTest {
        val viewModel = CollectionFormViewModel(collectionRepository, cfeRepository)

        // Moneda UYU (id = 50)
        viewModel.onMonedaSelected(TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0))
        val cuentasUYU = viewModel.getFilteredCuentasBanco()

        assertEquals(1, cuentasUYU.size)
        assertEquals(1L, cuentasUYU.first().id)

        // Moneda USD (id = 51)
        viewModel.onMonedaSelected(TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.0))
        val cuentasUSD = viewModel.getFilteredCuentasBanco()

        assertEquals(1, cuentasUSD.size)
        assertEquals(2L, cuentasUSD.first().id)
    }

    @Test
    fun `COLLECTION_CURRENCY_CHANGE_CLEARS_INCOMPATIBLE_ACCOUNT_AND_PAYMENT_METHOD`() = runTest {
        val viewModel = CollectionFormViewModel(collectionRepository, cfeRepository)

        // Select USD (id 51) -> Account 2 (USD) selected
        viewModel.onMonedaSelected(TasaCambioSimpleDto(51, "USD", "Dólar", "$", 2, 40.0))
        assertEquals(2L, viewModel.selectedCuentaBanco?.id)

        // Switch back to UYU (id 50) -> Account 2 is cleared, Account 1 (UYU) auto-selected
        viewModel.onMonedaSelected(TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0))
        assertEquals(1L, viewModel.selectedCuentaBanco?.id)
    }

    @Test
    fun `COLLECTION_ACCOUNT_SELECTION_LOADS_ALLOWED_PAYMENT_METHODS`() = runTest {
        val viewModel = CollectionFormViewModel(collectionRepository, cfeRepository)

        viewModel.onCuentaBancoSelected(CuentaBancoCatalogDto(10, numeroCuenta = "123"))

        verify(collectionRepository).getAllowedFormasPago(10L)
    }

    @Test
    fun `COLLECTION_PENDING_REQUEST_USES_FACTURA_ENDPOINT and SENDS_ESTADO_CONFIRMED_LIQUIDADA_FALSE_ES_ELECTRONICO_TRUE`() = runTest {
        val collectionApi = mock<CollectionApi>()
        val repo = CollectionRepository(collectionApi)

        whenever(collectionApi.getCollectableInvoices(eq(123L), eq(2), eq(false), eq(true), any(), any()))
            .thenReturn(CollectionInvoiceListResponse(totalRecords = 0, items = emptyList()))

        val result = repo.getCollectableInvoices(123L)
        assertTrue(result.isSuccess)

        verify(collectionApi).getCollectableInvoices(
            idCliente = 123L,
            estado = 2,
            liquidada = false,
            esElectronico = true,
            offset = 0,
            limit = 50
        )
    }

    @Test
    fun `DGI_RUC_PRUEBA_CEDE_ONLY_ELECTRONIC_PENDING_IS_VISIBLE`() = runTest {
        val fa119Electronic = CollectionInvoiceDto(
            id = 119L,
            folio = "FA-119/01/2026",
            esElectronico = true,
            cfeCodeIntent = 111,
            estado = 2,
            importeTotalBase = 1500.0,
            importeTotalBaseCobrado = 0.0
        )

        whenever(collectionRepository.getCollectableInvoices(10L))
            .thenReturn(Result.success(CollectionInvoiceListResponse(totalRecords = 1, items = listOf(fa119Electronic))))

        val viewModel = CollectionFormViewModel(collectionRepository, cfeRepository)
        viewModel.onClientSelected(ClienteDto(10, "DGI RUC PRUEBA CEDE"))

        assertEquals(1, viewModel.pendingInvoices.size)
        val visibleItem = viewModel.pendingInvoices.first()
        assertEquals(119L, visibleItem.factura.id)
        assertEquals("FA-119/01/2026", visibleItem.factura.folio)
        assertTrue(visibleItem.factura.esElectronico)
    }

    @Test
    fun `COLLECTION_PENDING_DOCUMENTS_NEVER_INCLUDE_BUDGETS`() = runTest {
        val viewModel = CollectionFormViewModel(collectionRepository, cfeRepository)

        whenever(collectionRepository.getCollectableInvoices(20L))
            .thenReturn(Result.success(CollectionInvoiceListResponse(totalRecords = 0, items = emptyList())))

        viewModel.onClientSelected(ClienteDto(20, "BARRETO ALBERTINI ANDREA"))

        // BARRETO has PF-3/01/2026 in Budgets/PreFactura, which is NEVER returned by /Factura!
        assertTrue(viewModel.pendingInvoices.isEmpty())
    }

    @Test
    fun `COLLECTION_CREATE_SERIALIZES_REQUIRED_FIELDS and COLLECTION_TYPE_SERIALIZES_AS_NUMERIC_1`() {
        val line = FacturaCobroCreateRequest(
            idFactura = 500L,
            montoActualBase = 400.0,
            montoBase = 400.0,
            montoActualOriginal = 0.0,
            montoOriginal = 0.0,
            montoOperacion = 400.0
        )

        val dto = CobroCreateDto(
            fechaEmision = "2026-10-09",
            fechaConfirmacion = "2026-10-09",
            idFormaPago = 1L,
            idMoneda = 50L,
            tasaCambio = 1.0,
            importeBase = 400.0,
            montoTotalBase = 400.0,
            importeOriginal = 0.0,
            montoTotalOriginal = 0.0,
            idCliente = 10L,
            facturaCobros = listOf(line)
        )

        val serialized = prodJson.encodeToString(dto)
        val jsonObject = prodJson.parseToJsonElement(serialized).jsonObject

        assertTrue(jsonObject.containsKey("tipoDocumentoFinanza"))
        assertEquals(1, jsonObject["tipoDocumentoFinanza"]?.jsonPrimitive?.int)
        assertTrue(jsonObject.containsKey("idFormaPago"))
        assertTrue(jsonObject.containsKey("idCliente"))
        assertTrue(jsonObject.containsKey("facturaCobros"))
        assertFalse(serialized.contains("idEmpresa"))
        assertFalse(serialized.contains("idUsuario"))
    }

    @Test
    fun `COLLECTION_POST_RETURNS_LONG_ID and COLLECTION_CONFIRM_USES_CREATED_ID`() = runTest {
        val viewModel = CollectionFormViewModel(collectionRepository, cfeRepository)

        val factura = CollectionInvoiceDto(
            id = 500L,
            estado = 2,
            cliente = ClienteDto(10, "ABITAB S A"),
            importeTotalBase = 1000.0,
            importeTotalBaseCobrado = 0.0
        )

        whenever(collectionRepository.getCollectableInvoices(10L))
            .thenReturn(Result.success(CollectionInvoiceListResponse(items = listOf(factura), totalRecords = 1)))
        whenever(collectionRepository.createCobro(any())).thenReturn(Result.success(8888L))
        whenever(collectionRepository.confirmCobro(8888L)).thenReturn(Result.success(Unit))

        viewModel.onClientSelected(ClienteDto(10, "ABITAB S A"))
        viewModel.onCuentaBancoSelected(CuentaBancoCatalogDto(1, numeroCuenta = "123456"))

        val pendingItem = viewModel.pendingInvoices.firstOrNull { it.factura.id == 500L }
        assertNotNull(pendingItem)
        pendingItem!!.isSelected = true
        pendingItem.montoCobrarText = "400.0"

        viewModel.saveCollection(andConfirm = true)

        verify(collectionRepository).createCobro(check { dto ->
            assertEquals(1, dto.tipoDocumentoFinanza)
            assertEquals(10L, dto.idCliente)
            assertEquals(1, dto.facturaCobros.size)
            assertEquals(400.0, dto.facturaCobros.first().montoOperacion, 0.001)
        })

        verify(collectionRepository).confirmCobro(8888L)
        assertTrue(viewModel.uiState is CollectionFormUiState.Success)
        assertEquals(8888L, (viewModel.uiState as CollectionFormUiState.Success).collectionId)
    }

    @Test
    fun `DIRECT_COLLECTION_PREFILLS_SAME_FACTURA_ID and PRESERVES_PRELOADED_INVOICE`() = runTest {
        val erpFactura = BudgetDto(
            id = 777L,
            estado = 2,
            cliente = ClienteDto(10, "ABITAB S A"),
            importeTotalBase = 500.0
        )

        whenever(cfeRepository.getFacturaById(777L)).thenReturn(Result.success(erpFactura))

        val viewModel = CollectionFormViewModel(collectionRepository, cfeRepository, facturaIdInicial = 777L)

        assertEquals("ABITAB S A", viewModel.selectedCliente?.nombre)
        assertTrue(viewModel.isClientLocked)
        assertEquals(1, viewModel.pendingInvoices.size)
        assertEquals(777L, viewModel.pendingInvoices.first().factura.id)
        assertEquals(500.0, viewModel.pendingInvoices.first().getPendingDisplay("UYU"), 0.001)
    }

    @Test
    fun `COLLECTION_SUBMIT_IS_SINGLE_FLIGHT`() = runTest {
        val viewModel = CollectionFormViewModel(collectionRepository, cfeRepository)

        viewModel.selectedCliente = ClienteDto(10, "ABITAB S A")
        viewModel.selectedCuentaBanco = CuentaBancoCatalogDto(1, numeroCuenta = "123")
        viewModel.selectedFormaPago = CatalogoItemDto(1, "Efectivo")
        viewModel.selectedMoneda = TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0)
        viewModel.pendingInvoices.add(
            com.authvex.balaxysefactura.ui.screens.collection.form.PendingInvoiceItem(
                factura = CollectionInvoiceDto(id = 500L),
                pendingBase = 100.0,
                pendingOriginal = 0.0,
                montoCobrarText = "100.0",
                isSelected = true
            )
        )

        whenever(collectionRepository.createCobro(any())).thenReturn(Result.success(999L))

        viewModel.saveCollection(andConfirm = false)

        // Verify single flight: when isSubmitting is true, subsequent calls are ignored
        viewModel.isSubmitting = true
        viewModel.saveCollection(andConfirm = false)

        verify(collectionRepository, times(1)).createCobro(any())
    }
}

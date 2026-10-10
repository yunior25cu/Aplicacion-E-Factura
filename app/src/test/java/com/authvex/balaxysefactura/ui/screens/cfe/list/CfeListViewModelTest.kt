package com.authvex.balaxysefactura.ui.screens.cfe.list

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CfeListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeCfeRepository(
        private val pageProvider: (query: String?, page: Int) -> Result<CfeSearchResponse>
    ) : CfeRepository(mockApi()) {
        override suspend fun searchDocuments(query: String?, page: Int, ordering: String?, sortDirection: String?): Result<CfeSearchResponse> {
            return pageProvider(query, page)
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is LoadingInitial and then Success when repository returns items`() = runTest {
        val docs = listOf(CfeSummaryDto(1, "A", 1L, 101, "Test", "2023-01-01", 100.0, "$", 6))
        val response = CfeSearchResponse(totalRecords = 1, offset = 0, limit = 20, items = docs)
        val viewModel = CfeListViewModel(FakeCfeRepository { _, _ -> Result.success(response) })
        
        assertEquals(CfeListUiState.LoadingInitial, viewModel.uiState)
        
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState is CfeListUiState.Success)
        val successState = viewModel.uiState as CfeListUiState.Success
        assertEquals(docs, successState.documents)
        assertEquals(1, successState.totalRecords)
        assertFalse(successState.canLoadMore)
    }

    @Test
    fun `state is Empty when repository returns empty list`() = runTest {
        val response = CfeSearchResponse(totalRecords = 0, offset = 0, limit = 20, items = emptyList())
        val viewModel = CfeListViewModel(FakeCfeRepository { _, _ -> Result.success(response) })
        
        advanceUntilIdle()
        
        assertEquals(CfeListUiState.Empty, viewModel.uiState)
    }

    @Test
    fun `state is Error when repository returns failure`() = runTest {
        val error = AppError.Network
        val viewModel = CfeListViewModel(FakeCfeRepository { _, _ -> Result.failure(error) })
        
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState is CfeListUiState.Error)
        assertEquals(error, (viewModel.uiState as CfeListUiState.Error).error)
    }

    @Test
    fun `loadNextPage appends new items and deduplicates by documentoId`() = runTest {
        val page1Docs = (1..20).map { id -> CfeSummaryDto(id, "A", id.toLong(), 101, "Receptor $id", "2026-10-07", 100.0, "$", 4) }
        // Page 2 contains items 21..25 plus duplicate item 20
        val page2Docs = (20..25).map { id -> CfeSummaryDto(id, "A", id.toLong(), 101, "Receptor $id", "2026-10-07", 100.0, "$", 4) }

        val repo = FakeCfeRepository { _, page ->
            if (page == 1) {
                Result.success(CfeSearchResponse(totalRecords = 25, offset = 0, limit = 20, items = page1Docs))
            } else {
                Result.success(CfeSearchResponse(totalRecords = 25, offset = 20, limit = 20, items = page2Docs))
            }
        }

        val viewModel = CfeListViewModel(repo)
        advanceUntilIdle()

        var state = viewModel.uiState as CfeListUiState.Success
        assertEquals(20, state.documents.size)
        assertTrue(state.canLoadMore)

        viewModel.loadNextPage()
        advanceUntilIdle()

        state = viewModel.uiState as CfeListUiState.Success
        // 20 items from page 1 + 5 new unique items from page 2 (item 20 deduplicated) = 25
        assertEquals(25, state.documents.size)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun `search query change resets to page 1 and loads filtered items`() = runTest {
        var capturedQuery: String? = null
        var capturedPage: Int? = null

        val repo = FakeCfeRepository { query, page ->
            capturedQuery = query
            capturedPage = page
            Result.success(CfeSearchResponse(totalRecords = 5, offset = 0, limit = 20, items = listOf(
                CfeSummaryDto(10, "A", 10L, 111, "Query Test", "2026-10-07", 500.0, "$", 4)
            )))
        }

        val viewModel = CfeListViewModel(repo)
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("10084")
        advanceUntilIdle()

        assertEquals("10084", capturedQuery)
        assertEquals(1, capturedPage)

        val state = viewModel.uiState as CfeListUiState.Success
        assertEquals(1, state.documents.size)
        assertEquals("Query Test", state.documents[0].receptor)
    }
}

private fun mockApi(): CfeApi = object : CfeApi {
    override suspend fun search(request: CfeSearchRequest): CfeSearchResponse = throw Exception()
    override suspend fun getDocument(documentoId: Int): CfeDetailDto = throw Exception()
    override suspend fun getFacturaById(id: Long): BudgetDto = throw Exception()
    override suspend fun getDevolucionById(id: Long): BudgetDto = throw Exception()
    override suspend fun getTasaCambioConfig(): TasaCambioConfigDto = throw Exception()
    override suspend fun syncBcuRate(request: BcuSyncRequest): BcuSyncResponse = throw Exception()
    override suspend fun downloadCfePdf(documentId: Long, redirect: Boolean): retrofit2.Response<okhttp3.ResponseBody> = throw Exception()
    override suspend fun createFactura(request: FacturaCreateDto): Long = throw Exception()
    override suspend fun createFacturaElectronicDraft(request: FacturaCreateDto): FacturaResponse = throw Exception()
    override suspend fun createDevolucion(request: DevolucionCreateDto): FacturaResponse = throw Exception()
    override suspend fun getFactura(documentoId: Long): FacturaResponse = throw Exception()
    override suspend fun getDevolucion(documentoId: Long): FacturaResponse = throw Exception()
    override suspend fun getClientes(query: String?, limit: Int): PagedResponse<ClienteDto> = throw Exception()
    override suspend fun getProductos(query: String?, limit: Int): PagedResponse<ProductoDto> = throw Exception()
    override suspend fun getMonedas(): List<CatalogoItemDto> = throw Exception()
    override suspend fun getTasaCambios(fecha: String): List<TasaCambioSimpleDto> = throw Exception()
    override suspend fun getTasaCambioLegacy(fecha: String): Double = throw Exception()
    override suspend fun getAlmacenes(): List<CatalogoItemDto> = throw Exception()
    override suspend fun getFormasPago(): List<CatalogoItemDto> = throw Exception()
    override suspend fun getVencimientos(): List<CatalogoItemDto> = throw Exception()
    override suspend fun getListasPrecio(): List<CatalogoItemDto> = throw Exception()
    override suspend fun getVendedores(cargo: String): List<CatalogoItemDto> = throw Exception()
    override suspend fun getCentroCostos(): List<CatalogoItemDto> = throw Exception()
    override suspend fun getEmpresa(): EmpresaDto = throw Exception()
    override suspend fun getPuntosVenta(): List<PuntoVentaDto> = throw Exception()
    override suspend fun getDocumentosHabilitados(puntoVentaId: Int): List<CfeFiscalDocumentAvailabilityGroupDto> = throw Exception()
    override suspend fun validateCfe(idDocumento: Long, cfeCode: Int, puntoVentaId: Int, seriePreferida: String?): CfeValidateResponseDto = throw Exception()
    override suspend fun emitCfe(documentoId: Long, request: CfeEmitRequest): CfeEmitResponse = throw Exception()
    override suspend fun getCfeStatus(documentoId: Long): CfeStatusResponse = throw Exception()
    override suspend fun getCfeStatusByUrl(statusUrl: String): CfeStatusResponse = throw Exception()
    override suspend fun getCfeStatusSync(documentoId: Long): CfeStatusResponse = throw Exception()
    override suspend fun getTiposPermitidos(onlyImplemented: Boolean): List<CfeTipoPermitidoDto> = throw Exception()
    override suspend fun caePrecheck(puntoVentaId: Int, tipoCfe: Int, serie: String?, fechaEmision: String): CaePrecheckResultDto = throw Exception()
    override suspend fun getIndicadoresFacturacion(cfeCode: Int, puntoVentaId: Int, seriePreferida: String?, fechaEmision: String): List<CfeFiscalIndicadorFacturacionDto> = throw Exception()
    override suspend fun getIndicadorSugerido(cfeCode: Int, tasaIva: Double, currentValue: Int?, puntoVentaId: Int, seriePreferida: String?, fechaEmision: String): CfeFiscalIndicadorSugeridoDto = throw Exception()
}

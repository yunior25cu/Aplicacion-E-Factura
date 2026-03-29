package com.authvex.balaxysefactura.ui.screens.cfe.list

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CfeListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeCfeRepository(
        private val result: Result<CfeSearchResponse>
    ) : CfeRepository(mockApi()) {
        override suspend fun searchDocuments(query: String?, page: Int): Result<CfeSearchResponse> = result
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
    fun `initial state is Loading and then Success when repository returns items`() = runTest {
        val docs = listOf(CfeSummaryDto(1, "A", 1L, 101, "Test", "2023-01-01", 100.0, "$", 6))
        val response = CfeSearchResponse(totalRecords = 1, offset = 0, limit = 20, items = docs)
        val viewModel = CfeListViewModel(FakeCfeRepository(Result.success(response)))
        
        assertEquals(CfeListUiState.Loading, viewModel.uiState)
        
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState is CfeListUiState.Success)
        val successState = viewModel.uiState as CfeListUiState.Success
        assertEquals(docs, successState.documents)
        assertEquals(1, successState.totalRecords)
    }

    @Test
    fun `state is Empty when repository returns empty list`() = runTest {
        val response = CfeSearchResponse(totalRecords = 0, offset = 0, limit = 20, items = emptyList())
        val viewModel = CfeListViewModel(FakeCfeRepository(Result.success(response)))
        
        advanceUntilIdle()
        
        assertEquals(CfeListUiState.Empty, viewModel.uiState)
    }

    @Test
    fun `state is Error when repository returns failure`() = runTest {
        val error = AppError.Network
        val viewModel = CfeListViewModel(FakeCfeRepository(Result.failure(error)))
        
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState is CfeListUiState.Error)
        assertEquals(error, (viewModel.uiState as CfeListUiState.Error).error)
    }
}

private fun mockApi(): CfeApi = object : CfeApi {
    override suspend fun search(request: CfeSearchRequest): CfeSearchResponse = throw Exception()
    override suspend fun getDocument(documentoId: Int): CfeDetailDto = throw Exception()
    override suspend fun createFactura(request: FacturaRequest): Long = throw Exception()
    override suspend fun getFactura(documentoId: Long): FacturaResponse = throw Exception()
    override suspend fun getClientes(filtro: String?): PagedResponse<ClienteDto> = throw Exception()
    override suspend fun getProductos(filtro: String?): PagedResponse<ProductoDto> = throw Exception()
    override suspend fun getMonedas(): List<CatalogoItemDto> = throw Exception()
    override suspend fun getTasaCambio(fecha: String): Double = throw Exception()
    override suspend fun getAlmacenes(): List<CatalogoItemDto> = throw Exception()
    override suspend fun getFormasPago(): List<CatalogoItemDto> = throw Exception()
    override suspend fun getVencimientos(): List<CatalogoItemDto> = throw Exception()
    override suspend fun getListasPrecio(): List<CatalogoItemDto> = throw Exception()
    override suspend fun getVendedores(cargo: String): List<CatalogoItemDto> = throw Exception()
    override suspend fun getCentroCostos(): List<CatalogoItemDto> = throw Exception()
    override suspend fun getTiposPermitidos(onlyImplemented: Boolean): List<CfeTipoPermitidoDto> = throw Exception()
    override suspend fun emitCfe(documentoId: Long, request: CfeEmitRequest): CfeEmitResponse = throw Exception()
    override suspend fun getCfeStatus(documentoId: Long): CfeStatusResponse = throw Exception()
}

package com.authvex.balaxysefactura.ui.screens.cfe.detail

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
class CfeDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeCfeRepository(
        private val result: Result<CfeDetailDto>
    ) : CfeRepository(mockApi()) {
        override suspend fun getDocumentDetail(documentoId: Int): Result<CfeDetailDto> = result
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
    fun `initial state is Loading and then Success when repository returns detail`() = runTest {
        val detail = CfeDetailDto(
            documentoId = 1,
            serie = "A",
            numero = 1L,
            cfeCode = 111,
            estadoCfe = 6,
            estadoReceptor = 1,
            receptor = "Test Client",
            fechaEmision = "2023-01-01",
            fechaConfirmacion = null,
            fechaEnvioUtc = null,
            fechaAceptadoUtc = null,
            importeTotal = 100.0,
            iva = 22.0,
            monedaCodigo = "UYU",
            monedaSimbolo = "$",
            ultimoError = null
        )
        val viewModel = CfeDetailViewModel(FakeCfeRepository(Result.success(detail)), 1)
        
        assertEquals(CfeDetailUiState.Loading, viewModel.uiState)
        
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState is CfeDetailUiState.Success)
        assertEquals(detail, (viewModel.uiState as CfeDetailUiState.Success).document)
    }

    @Test
    fun `state is Error when repository returns failure`() = runTest {
        val error = AppError.NotFound
        val viewModel = CfeDetailViewModel(FakeCfeRepository(Result.failure(error)), 1)
        
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState is CfeDetailUiState.Error)
        assertEquals(error, (viewModel.uiState as CfeDetailUiState.Error).error)
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

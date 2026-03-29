package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class CfeRepositoryTest {

    private class FakeCfeApi(
        private val searchResponse: CfeSearchResponse = CfeSearchResponse(0, 0, 20, emptyList()),
        private val detailResponse: CfeDetailDto? = null,
        private val shouldThrow: Throwable? = null
    ) : CfeApi {
        override suspend fun search(request: CfeSearchRequest): CfeSearchResponse {
            shouldThrow?.let { throw it }
            return searchResponse
        }

        override suspend fun getDocument(documentoId: Int): CfeDetailDto {
            shouldThrow?.let { throw it }
            return detailResponse ?: throw Exception("Not found")
        }

        private fun <T> paged(items: List<T>) = PagedResponse(items.size, 0, 999, items)

        override suspend fun createFactura(request: FacturaRequest): Long = shouldThrow?.let { throw it } ?: 1L
        override suspend fun getFactura(documentoId: Long): FacturaResponse = shouldThrow?.let { throw it } ?: FacturaResponse(documentoId)
        
        override suspend fun getClientes(filtro: String?): PagedResponse<ClienteDto> = 
            shouldThrow?.let { throw it } ?: paged(emptyList())
            
        override suspend fun getProductos(filtro: String?): PagedResponse<ProductoDto> = 
            shouldThrow?.let { throw it } ?: paged(emptyList())
            
        override suspend fun getMonedas(): List<CatalogoItemDto> = 
            shouldThrow?.let { throw it } ?: emptyList()
            
        override suspend fun getTasaCambio(fecha: String): Double = shouldThrow?.let { throw it } ?: 1.0
        
        override suspend fun getAlmacenes(): List<CatalogoItemDto> = 
            shouldThrow?.let { throw it } ?: emptyList()
            
        override suspend fun getFormasPago(): List<CatalogoItemDto> = 
            shouldThrow?.let { throw it } ?: emptyList()
            
        override suspend fun getVencimientos(): List<CatalogoItemDto> = 
            shouldThrow?.let { throw it } ?: emptyList()
            
        override suspend fun getListasPrecio(): List<CatalogoItemDto> = 
            shouldThrow?.let { throw it } ?: emptyList()
            
        override suspend fun getVendedores(cargo: String): List<CatalogoItemDto> = 
            shouldThrow?.let { throw it } ?: emptyList()
            
        override suspend fun getCentroCostos(): List<CatalogoItemDto> = 
            shouldThrow?.let { throw it } ?: emptyList()

        override suspend fun getTiposPermitidos(onlyImplemented: Boolean): List<CfeTipoPermitidoDto> = shouldThrow?.let { throw it } ?: emptyList()
        override suspend fun emitCfe(documentoId: Long, request: CfeEmitRequest): CfeEmitResponse = shouldThrow?.let { throw it } ?: CfeEmitResponse(true)
        override suspend fun getCfeStatus(documentoId: Long): CfeStatusResponse = shouldThrow?.let { throw it } ?: CfeStatusResponse(documentoId, 1)
    }

    @Test
    fun `searchDocuments returns success when api is successful`() = runBlocking {
        val docs = listOf(CfeSummaryDto(1, "A", 1L, 101, "Test", "2023-01-01", 100.0, "$", 6, 0))
        val response = CfeSearchResponse(totalRecords = 1, offset = 0, limit = 20, items = docs)
        val repository = CfeRepository(FakeCfeApi(searchResponse = response))
        
        val result = repository.searchDocuments()
        
        assertTrue(result.isSuccess)
        assertEquals(response, result.getOrNull())
    }

    @Test
    fun `searchDocuments returns failure with Network error when api throws UnknownHostException`() = runBlocking {
        val repository = CfeRepository(FakeCfeApi(shouldThrow = UnknownHostException()))
        
        val result = repository.searchDocuments()
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.Network)
    }

    @Test
    fun `getDocumentDetail returns success when api is successful`() = runBlocking {
        val detail = CfeDetailDto(1, "A", 1L, 111, 6, 1, "Test", "2023-01-01", null, null, null, 100.0, 22.0, "UYU", "$", null)
        val repository = CfeRepository(FakeCfeApi(detailResponse = detail))
        
        val result = repository.getDocumentDetail(1)
        
        assertTrue(result.isSuccess)
        assertEquals(detail, result.getOrNull())
    }

    @Test
    fun `regression - parses paged client with denominacion instead of nombre`() = runBlocking {
        val json = """
            {
                "totalRecords": 1,
                "offset": 0,
                "limit": 999,
                "items": [
                    {
                        "id": 15,
                        "denominacion": "Santander UY",
                        "codigo": "205241"
                    }
                ]
            }
        """.trimIndent()
        
        val format = Json { ignoreUnknownKeys = true }
        val pagedResponse = format.decodeFromString<PagedResponse<ClienteDto>>(json)
        
        assertEquals(1, pagedResponse.totalRecords)
        assertEquals("Santander UY", pagedResponse.items[0].nombre)
    }

    @Test
    fun `regression - parses simple list of currencies correctly`() = runBlocking {
        val json = """
            [
                {
                    "id": 1,
                    "codigo": "USD",
                    "denominacion": "Dólar"
                },
                {
                    "id": 33,
                    "codigo": "UYU",
                    "denominacion": "Peso"
                }
            ]
        """.trimIndent()
        
        val format = Json { ignoreUnknownKeys = true }
        val list = format.decodeFromString<List<CatalogoItemDto>>(json)
        
        assertEquals(2, list.size)
        assertEquals("USD", list[0].codigo)
        assertEquals("Dólar", list[0].nombre)
    }
}

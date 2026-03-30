package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException

class CfeRepositoryTest {

    // Simple fake implementation of CfeApi for testing.
    class FakeCfeApi : CfeApi {
        var shouldThrow: Exception? = null
        var lastSearchRequest: CfeSearchRequest? = null

        override suspend fun search(request: CfeSearchRequest): CfeSearchResponse {
            lastSearchRequest = request
            if (shouldThrow != null) throw shouldThrow!!
            return CfeSearchResponse(0, 0, 20, emptyList())
        }
        
        override suspend fun getDocument(documentoId: Int): CfeDetailDto = throw NotImplementedError()
        override suspend fun createFactura(request: FacturaCreateDto): Long = shouldThrow?.let { throw it } ?: 1L
        override suspend fun createDevolucion(request: DevolucionCreateDto): Long = shouldThrow?.let { throw it } ?: 1L
        override suspend fun getFactura(documentoId: Long): FacturaResponse = shouldThrow?.let { throw it } ?: FacturaResponse(documentoId)
        override suspend fun getDevolucion(documentoId: Long): FacturaResponse = shouldThrow?.let { throw it } ?: FacturaResponse(documentoId)

        override suspend fun getClientes(query: String?, limit: Int): PagedResponse<ClienteDto> = 
            shouldThrow?.let { throw it } ?: PagedResponse(0, 0, 20, emptyList())
            
        override suspend fun getProductos(query: String?, limit: Int): PagedResponse<ProductoDto> = 
            shouldThrow?.let { throw it } ?: PagedResponse(0, 0, 20, emptyList())
            
        override suspend fun getMonedas(): List<CatalogoItemDto> = 
            shouldThrow?.let { throw it } ?: emptyList()
            
        override suspend fun getTasaCambio(fecha: String): Double = 
            shouldThrow?.let { throw it } ?: 1.0
            
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

        override suspend fun getPuntosVenta(): List<PuntoVentaDto> = shouldThrow?.let { throw it } ?: emptyList()
        override suspend fun getDocumentosHabilitados(puntoVentaId: Int): List<CfeFiscalDocumentAvailabilityGroupDto> = shouldThrow?.let { throw it } ?: emptyList()
        override suspend fun validateCfe(idDocumento: Long, cfeCode: Int, puntoVentaId: Int, seriePreferida: String?): CfeValidateResponseDto = shouldThrow?.let { throw it } ?: CfeValidateResponseDto(true)
        override suspend fun emitCfe(documentoId: Long, request: CfeEmitRequest): CfeEmitResponse = shouldThrow?.let { throw it } ?: CfeEmitResponse("req-123", null)
        override suspend fun getCfeStatus(documentoId: Long): CfeStatusResponse = shouldThrow?.let { throw it } ?: CfeStatusResponse(documentoId, 1)
        override suspend fun getCfeStatusByUrl(statusUrl: String): CfeStatusResponse = shouldThrow?.let { throw it } ?: CfeStatusResponse(1L, 1)
        override suspend fun getCfeStatusSync(documentoId: Long): CfeStatusResponse = shouldThrow?.let { throw it } ?: CfeStatusResponse(documentoId, 1)
        override suspend fun getTiposPermitidos(onlyImplemented: Boolean): List<CfeTipoPermitidoDto> = shouldThrow?.let { throw it } ?: emptyList()
        
        override suspend fun caePrecheck(puntoVentaId: Int, tipoCfe: Int, serie: String?, fechaEmision: String): CaePrecheckResultDto = 
            shouldThrow?.let { throw it } ?: CaePrecheckResultDto(true)
            
        override suspend fun getIndicadoresFacturacion(cfeCode: Int, puntoVentaId: Int, seriePreferida: String?, fechaEmision: String): List<CfeFiscalIndicadorFacturacionDto> = 
            shouldThrow?.let { throw it } ?: emptyList()
            
        override suspend fun getIndicadorSugerido(cfeCode: Int, tasaIva: Double, currentValue: Int?, puntoVentaId: Int, seriePreferida: String?, fechaEmision: String): CfeFiscalIndicadorSugeridoDto = 
            shouldThrow?.let { throw it } ?: CfeFiscalIndicadorSugeridoDto(null, null, false, "N/A")
    }

    @Test
    fun `searchDocuments returns success when api is successful`() = runBlocking {
        val docs = listOf(CfeSummaryDto(1, "A", 1L, 101, "Test", "2023-01-01", 100.0, "$", 6, 0))
        val response = CfeSearchResponse(totalRecords = 1, offset = 0, limit = 20, items = docs)
        
        val fakeApi = FakeCfeApi()
        // Override search manually for this test case
        val specialApi = object : CfeApi by fakeApi {
            override suspend fun search(request: CfeSearchRequest): CfeSearchResponse {
                return response
            }
        }
        
        val repository = CfeRepository(specialApi)
        val result = repository.searchDocuments(null, 1)
        
        assertTrue(result.isSuccess)
        assertEquals(response, result.getOrNull())
    }

    @Test
    fun `searchDocuments returns failure when api throws`() = runBlocking {
        val fakeApi = FakeCfeApi().apply { shouldThrow = ConnectException("Network error") }
        val repository = CfeRepository(fakeApi)
        
        val result = repository.searchDocuments(null, 1)
        
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is AppError.Network)
    }

    @Test
    fun `searchDocuments passes correct parameters`() = runBlocking {
        val fakeApi = FakeCfeApi()
        val repository = CfeRepository(fakeApi)
        
        repository.searchDocuments("Query", 2)
        
        val request = fakeApi.lastSearchRequest
        assertNotNull(request)
        assertEquals("Query", request?.filtro)
        assertEquals(2, request?.pagina)
    }
}

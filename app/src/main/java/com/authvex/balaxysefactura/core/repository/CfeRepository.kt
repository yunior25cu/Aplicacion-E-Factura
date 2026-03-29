package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.*

open class CfeRepository(private val api: CfeApi) {

    open suspend fun searchDocuments(query: String? = null, page: Int = 1): Result<CfeSearchResponse> {
        return try {
            val response = api.search(CfeSearchRequest(pagina = page, filtro = query))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getDocumentDetail(documentoId: Int): Result<CfeDetailDto> {
        return try {
            val response = api.getDocument(documentoId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    // --- Emisión ERP ---
    open suspend fun createFactura(request: FacturaRequest): Result<Long> {
        return try {
            val documentoId = api.createFactura(request)
            Result.success(documentoId)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getFactura(documentoId: Long): Result<FacturaResponse> {
        return try {
            val response = api.getFactura(documentoId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    // --- Catálogos Paginados ---
    open suspend fun getClientes(filtro: String? = null): Result<List<ClienteDto>> {
        return try {
            Result.success(api.getClientes(filtro).items)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getProductos(filtro: String? = null): Result<List<ProductoDto>> {
        return try {
            Result.success(api.getProductos(filtro).items)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    // --- Catálogos de Lista Simple ---
    open suspend fun getMonedas(): Result<List<CatalogoItemDto>> {
        return try {
            Result.success(api.getMonedas())
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getAlmacenes(): Result<List<CatalogoItemDto>> {
        return try {
            Result.success(api.getAlmacenes())
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getFormasPago(): Result<List<CatalogoItemDto>> {
        return try {
            Result.success(api.getFormasPago())
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getVencimientos(): Result<List<CatalogoItemDto>> {
        return try {
            Result.success(api.getVencimientos())
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getListasPrecio(): Result<List<CatalogoItemDto>> {
        return try {
            Result.success(api.getListasPrecio())
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    // --- Flujo Fiscal ---
    open suspend fun getTiposPermitidos(): Result<List<CfeTipoPermitidoDto>> {
        return try {
            Result.success(api.getTiposPermitidos())
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun emitCfe(documentoId: Long, request: CfeEmitRequest): Result<CfeEmitResponse> {
        return try {
            val response = api.emitCfe(documentoId, request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getCfeStatus(documentoId: Long): Result<CfeStatusResponse> {
        return try {
            val response = api.getCfeStatus(documentoId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }
}

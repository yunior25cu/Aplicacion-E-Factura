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
    open suspend fun createFactura(request: FacturaCreateDto): Result<Long> {
        return try {
            val documentoId = api.createFactura(request)
            Result.success(documentoId)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun createDevolucion(request: DevolucionCreateDto): Result<Long> {
        return try {
            val documentoId = api.createDevolucion(request)
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

    open suspend fun getDevolucion(documentoId: Long): Result<FacturaResponse> {
        return try {
            val response = api.getDevolucion(documentoId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    // --- Catálogos Paginados ---
    open suspend fun getClientes(query: String? = null, limit: Int = 20): Result<List<ClienteDto>> {
        return try {
            Result.success(api.getClientes(query, limit).items)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getProductos(query: String? = null, limit: Int = 20): Result<List<ProductoDto>> {
        return try {
            Result.success(api.getProductos(query, limit).items)
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

    // --- Flujo Fiscal Actualizado ---
    open suspend fun getPuntosVenta(): Result<List<PuntoVentaDto>> {
        return try {
            Result.success(api.getPuntosVenta())
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getDocumentosHabilitados(puntoVentaId: Int): Result<List<CfeFiscalDocumentAvailabilityGroupDto>> {
        return try {
            Result.success(api.getDocumentosHabilitados(puntoVentaId))
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun validateCfe(idDocumento: Long, cfeCode: Int, puntoVentaId: Int, serie: String?): Result<CfeValidateResponseDto> {
        return try {
            Result.success(api.validateCfe(idDocumento, cfeCode, puntoVentaId, serie))
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

    open suspend fun caePrecheck(puntoVentaId: Int, tipoCfe: Int, serie: String?, fechaEmision: String): Result<CaePrecheckResultDto> {
        return try {
            Result.success(api.caePrecheck(puntoVentaId, tipoCfe, serie, fechaEmision))
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getIndicadoresFacturacion(cfeCode: Int, puntoVentaId: Int, seriePreferida: String?, fechaEmision: String): Result<List<CfeFiscalIndicadorFacturacionDto>> {
        return try {
            Result.success(api.getIndicadoresFacturacion(cfeCode, puntoVentaId, seriePreferida, fechaEmision))
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getIndicadorSugerido(cfeCode: Int, tasaIva: Double, currentValue: Int?, puntoVentaId: Int, seriePreferida: String?, fechaEmision: String): Result<CfeFiscalIndicadorSugeridoDto> {
        return try {
            Result.success(api.getIndicadorSugerido(cfeCode, tasaIva, currentValue, puntoVentaId, seriePreferida, fechaEmision))
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    // --- Retrocompatibilidad ---
    open suspend fun getTiposPermitidos(): Result<List<CfeTipoPermitidoDto>> {
        return try {
            Result.success(api.getTiposPermitidos())
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }
}

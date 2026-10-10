package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.*

open class CollectionRepository(private val collectionApi: CollectionApi) {

    open suspend fun getCobros(
        pagina: Int = 1,
        registrosPorPagina: Int = 20,
        busqueda: String? = null,
        estado: Int? = null,
        idCliente: Long? = null,
        ordering: String? = "Documento.Numero",
        sortDirection: String? = "desc"
    ): Result<CobroListResponse> {
        return try {
            val offset = (pagina - 1) * registrosPorPagina
            val queryMap = mutableMapOf<String, String>(
                "offset" to offset.toString(),
                "limit" to registrosPorPagina.toString(),
                "TipoDocumentoFinanza" to "1"
            )
            if (!busqueda.isNullOrBlank()) queryMap["query"] = busqueda.trim()
            if (estado != null) queryMap["estado"] = estado.toString()
            if (idCliente != null) queryMap["idCliente"] = idCliente.toString()
            if (!ordering.isNullOrBlank()) queryMap["ordering"] = ordering
            if (!sortDirection.isNullOrBlank()) queryMap["sortDirection"] = sortDirection

            val response = collectionApi.getCobros(queryMap)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getCobroById(id: Long): Result<CobroDetailDto> {
        return try {
            val response = collectionApi.getCobroById(id)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun createCobro(request: CobroCreateDto): Result<Long> {
        return try {
            val createdId = collectionApi.createCobro(request)
            Result.success(createdId)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun confirmCobro(id: Long): Result<Unit> {
        return try {
            collectionApi.confirmCobro(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getCuentasBanco(): Result<List<CuentaBancoCatalogDto>> {
        return try {
            val response = collectionApi.getCuentasBanco()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getAllowedFormasPago(cuentaId: Long): Result<List<CatalogoItemDto>> {
        return try {
            val response = collectionApi.getAllowedFormasPago(cuentaId, tipoOperacion = 1)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getCollectableInvoices(idCliente: Long): Result<CollectionInvoiceListResponse> {
        return try {
            val response = collectionApi.getCollectableInvoices(
                idCliente = idCliente,
                estado = 2,
                liquidada = false,
                esElectronico = true,
                offset = 0,
                limit = 50
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getInvoiceById(id: Long): Result<CollectionInvoiceDto> {
        return try {
            val response = collectionApi.getInvoiceById(id)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }
}

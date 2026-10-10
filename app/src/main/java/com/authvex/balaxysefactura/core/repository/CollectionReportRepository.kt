package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.*

open class CollectionReportRepository(
    private val api: CollectionReportApi,
    private val collectionApi: CollectionApi
) {

    open suspend fun getAccountsReceivable(
        fechaDesde: String,
        fechaHasta: String,
        idCliente: Long? = null,
        idMoneda: Long? = null,
        estado: Int? = 1
    ): Result<List<CuentasPorCobrarDto>> {
        return try {
            val response = api.getAccountsReceivable(
                fechaDesde = fechaDesde,
                fechaHasta = fechaHasta,
                idCliente = idCliente,
                idMoneda = idMoneda,
                estado = estado,
                estadoVencimiento = 0,
                tipoReporte = 2 // Agrupado
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getAccountsReceivableAging(
        fechaDesde: String,
        fechaHasta: String,
        idCliente: Long? = null,
        idMoneda: Long? = null,
        estado: Int = 1
    ): Result<List<CuentasPorCobrarAgingDto>> {
        return try {
            val response = api.getAccountsReceivableAging(
                fechaDesde = fechaDesde,
                fechaHasta = fechaHasta,
                idCliente = idCliente,
                idMoneda = idMoneda,
                estado = estado,
                estadoVencimiento = 0
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getCollectedSummaryExhaustive(
        fechaDesde: String,
        fechaHasta: String,
        idMoneda: Long? = null,
        idCliente: Long? = null
    ): Result<List<CobroSummaryDto>> {
        return try {
            val allItems = mutableListOf<CobroSummaryDto>()
            var page = 1
            val pageSize = 100
            var totalRecords = 1

            while (allItems.size < totalRecords) {
                val offset = (page - 1) * pageSize
                val queryMap = mutableMapOf<String, String>(
                    "offset" to offset.toString(),
                    "limit" to pageSize.toString(),
                    "TipoDocumentoFinanza" to "1",
                    "Estado" to "2",
                    "FechaDesde" to fechaDesde,
                    "FechaHasta" to fechaHasta,
                    "Ordering" to "Documento.Numero",
                    "sortDirection" to "desc"
                )
                if (idMoneda != null) queryMap["IdMoneda"] = idMoneda.toString()
                if (idCliente != null) queryMap["IdCliente"] = idCliente.toString()

                val pageRes = collectionApi.getCobros(queryMap)
                totalRecords = pageRes.totalRecords
                if (pageRes.items.isEmpty()) break

                allItems.addAll(pageRes.items)
                page++
            }

            Result.success(allItems)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }
}

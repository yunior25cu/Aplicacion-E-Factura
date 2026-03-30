package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.*

class ReportsRepository(private val api: ReportsApi) {

    suspend fun getVentasResumen(
        fechaDesde: String,
        fechaHasta: String,
        almacenId: Int? = null,
        clienteId: Int? = null,
        soloElectronicas: Boolean? = null
    ): Result<VentasResumenResponse> {
        return try {
            Result.success(api.getVentasResumen(fechaDesde, fechaHasta, almacenId, clienteId, soloElectronicas))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVentasSerie(
        fechaDesde: String,
        fechaHasta: String,
        granularidad: String? = null,
        almacenId: Int? = null,
        clienteId: Int? = null,
        soloElectronicas: Boolean? = null
    ): Result<VentasSerieResponse> {
        return try {
            Result.success(api.getVentasSerie(fechaDesde, fechaHasta, granularidad, almacenId, clienteId, soloElectronicas))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVentasPorCliente(
        fechaDesde: String,
        fechaHasta: String,
        almacenId: Int? = null,
        soloElectronicas: Boolean? = null,
        top: Int = 10
    ): Result<VentasPorClienteResponse> {
        return try {
            Result.success(api.getVentasPorCliente(fechaDesde, fechaHasta, almacenId, soloElectronicas, top))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVentasPorProducto(
        fechaDesde: String,
        fechaHasta: String,
        almacenId: Int? = null,
        categoriaId: Int? = null,
        subcategoriaId: Int? = null,
        soloElectronicas: Boolean? = null,
        top: Int = 10
    ): Result<VentasPorProductoResponse> {
        return try {
            Result.success(api.getVentasPorProducto(fechaDesde, fechaHasta, almacenId, categoriaId, subcategoriaId, soloElectronicas, top))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVentasPorDocumento(
        fechaDesde: String,
        fechaHasta: String,
        almacenId: Int? = null,
        soloElectronicas: Boolean? = null
    ): Result<VentasPorDocumentoResponse> {
        return try {
            Result.success(api.getVentasPorDocumento(fechaDesde, fechaHasta, almacenId, soloElectronicas))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCategorias(id: Int? = null): Result<List<CategoriaDto>> {
        return try {
            Result.success(api.getCategorias(id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

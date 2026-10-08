package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.BudgetApi
import com.authvex.balaxysefactura.core.network.BudgetCreateDto
import com.authvex.balaxysefactura.core.network.BudgetDto
import com.authvex.balaxysefactura.core.network.BudgetFacturarDto
import com.authvex.balaxysefactura.core.network.BudgetInvoiceOptionsDto
import com.authvex.balaxysefactura.core.network.BudgetListResponse

class BudgetRepository(private val budgetApi: BudgetApi) {

    suspend fun getBudgets(
        pagina: Int = 1,
        registrosPorPagina: Int = 20,
        busqueda: String? = null,
        estado: Int? = null,
        idCliente: Long? = null
    ): Result<BudgetListResponse> {
        return try {
            val queryMap = mutableMapOf<String, String>(
                "pagina" to pagina.toString(),
                "registrosPorPagina" to registrosPorPagina.toString()
            )
            if (!busqueda.isNullOrBlank()) {
                queryMap["busqueda"] = busqueda.trim()
            }
            if (estado != null) {
                queryMap["estado"] = estado.toString()
            }
            if (idCliente != null) {
                queryMap["idCliente"] = idCliente.toString()
            }

            val response = budgetApi.getBudgets(queryMap)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBudgetById(id: Long): Result<BudgetDto> {
        return try {
            val dto = budgetApi.getBudgetById(id)
            Result.success(dto)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createBudget(dto: BudgetCreateDto): Result<Long> {
        return try {
            val id = budgetApi.createBudget(dto)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBudget(dto: com.authvex.balaxysefactura.core.network.BudgetUpdateDto): Result<Unit> {
        return try {
            budgetApi.updateBudget(dto)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun confirmBudget(id: Long): Result<Unit> {
        return try {
            budgetApi.confirmBudget(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelBudget(id: Long): Result<Unit> {
        return try {
            budgetApi.cancelBudget(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getInvoiceOptions(id: Long): Result<BudgetInvoiceOptionsDto> {
        return try {
            val options = budgetApi.getInvoiceOptions(id)
            Result.success(options)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun invoiceBudget(id: Long, dto: BudgetFacturarDto): Result<Long> {
        return try {
            val facturaId = budgetApi.invoiceBudget(id, dto)
            Result.success(facturaId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

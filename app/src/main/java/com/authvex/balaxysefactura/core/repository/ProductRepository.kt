package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.*

open class ProductRepository(
    private val productApi: ProductApi
) {

    open suspend fun createProduct(request: ProductCreateRequest): Result<Long> {
        return try {
            val id = productApi.createProduct(request)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getProductById(idProducto: Long): Result<ProductoDto> {
        return try {
            val product = productApi.getProductById(idProducto)
            Result.success(product)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getUnidadesMedida(): Result<List<CatalogoItemDto>> {
        return try {
            val unidades = productApi.getUnidadesMedida()
            Result.success(unidades)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getTiposProducto(): Result<List<CatalogoItemDto>> {
        return try {
            val tipos = productApi.getTiposProducto()
            Result.success(tipos)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getImpuestosVenta(): Result<List<CatalogoItemDto>> {
        return try {
            val impuestos = productApi.getImpuestosVenta()
            Result.success(impuestos)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }
}

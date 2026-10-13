package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.*

open class ClientRepository(
    private val clientApi: ClientApi
) {

    open suspend fun createClient(request: ClientCreateRequest): Result<Unit> {
        return try {
            val response = clientApi.createClient(request)
            if (response.isSuccessful || response.code() == 204) {
                Result.success(Unit)
            } else {
                val code = response.code()
                val errorBody = try { response.errorBody()?.string() ?: "" } catch (_: Exception) { "" }
                val appError = ErrorMapper.fromHttpResponseBody(code, errorBody)
                Result.failure(appError)
            }
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getThirdPartyRegistralData(rut: String): Result<DgiPersonaActEmpresarialResponseDto> {
        return try {
            val response = clientApi.getThirdPartyRegistralData(rut)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }
}

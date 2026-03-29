package com.authvex.balaxysefactura.core.network

import kotlinx.serialization.json.*
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun fromThrowable(t: Throwable): AppError {
        return when (t) {
            is UnknownHostException, is ConnectException -> AppError.Network
            is SocketTimeoutException -> AppError.Timeout
            is HttpException -> fromHttpException(t)
            is AppError -> t
            else -> AppError.Unexpected(t.message ?: "Error desconocido")
        }
    }

    private fun fromHttpException(e: HttpException): AppError {
        val code = e.code()
        val errorBody = e.response()?.errorBody()?.string() ?: ""

        return when (code) {
            401 -> AppError.Unauthorized
            403 -> {
                val detail = parseJsonDetail(errorBody)
                AppError.Forbidden(detail)
            }
            404 -> AppError.NotFound
            409 -> AppError.Conflict
            400 -> parseBadRequest(errorBody)
            in 500..599 -> AppError.ServerError(code)
            else -> AppError.Unexpected("Error HTTP: $code")
        }
    }

    private fun parseJsonDetail(errorBody: String): String? {
        if (errorBody.isBlank()) return null
        return try {
            val jsonElement = json.parseToJsonElement(errorBody).jsonObject
            jsonElement["detail"]?.jsonPrimitive?.content
                ?: jsonElement["message"]?.jsonPrimitive?.content
        } catch (ex: Exception) {
            null
        }
    }

    private fun parseBadRequest(errorBody: String): AppError {
        if (errorBody.isBlank()) return AppError.Validation("Solicitud incorrecta")
        
        return try {
            val jsonElement = json.parseToJsonElement(errorBody).jsonObject
            
            // 1. Intentar buscar "detail" (ProblemDetails) o "message"
            val message = jsonElement["detail"]?.jsonPrimitive?.content
                ?: jsonElement["message"]?.jsonPrimitive?.content
            
            // 2. Intentar buscar "errors" (ModelState)
            val errorsMap = jsonElement["errors"]?.jsonObject?.mapValues { entry ->
                entry.value.jsonArray.map { it.jsonPrimitive.content }
            }

            if (message != null || errorsMap != null) {
                AppError.Validation(message ?: "Error de validación", errorsMap)
            } else {
                // Si es un JSON pero no tiene los campos esperados, devolver el texto crudo
                AppError.Validation(errorBody)
            }
        } catch (ex: Exception) {
            // Si no es JSON, es un string plano
            AppError.Validation(errorBody)
        }
    }
}

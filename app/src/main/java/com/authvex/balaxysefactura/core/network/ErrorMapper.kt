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

    fun fromHttpResponseBody(code: Int, errorBody: String): AppError {
        return when (code) {
            401 -> AppError.Unauthorized
            403 -> parseForbidden(errorBody)
            404 -> AppError.NotFound(parseJsonDetail(errorBody))
            409 -> AppError.Conflict(parseJsonDetail(errorBody))
            400, 422 -> parseBadRequest(errorBody)
            in 500..599 -> AppError.ServerError(code, parseJsonDetail(errorBody))
            else -> {
                val detail = parseJsonDetail(errorBody)
                if (detail != null) AppError.Unexpected(detail) else AppError.Unexpected("Error HTTP: $code")
            }
        }
    }

    private fun fromHttpException(e: HttpException): AppError {
        val code = e.code()
        val errorBody = try {
            e.response()?.errorBody()?.string() ?: ""
        } catch (_: Exception) {
            ""
        }
        return fromHttpResponseBody(code, errorBody)
    }

    fun parseJsonDetail(errorBody: String): String? {
        if (errorBody.isBlank()) return null
        return try {
            val jsonElement = json.parseToJsonElement(errorBody).jsonObject
            val detail = jsonElement["detail"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val message = jsonElement["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val title = jsonElement["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

            detail ?: message ?: title
        } catch (ex: Exception) {
            null
        }
    }

    private fun parseForbidden(errorBody: String): AppError.Forbidden {
        if (errorBody.isBlank()) return AppError.Forbidden()
        return try {
            val jsonElement = json.parseToJsonElement(errorBody).jsonObject
            val detail = jsonElement["detail"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val message = jsonElement["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val title = jsonElement["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val reasonCode = jsonElement["code"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

            val mainText = detail ?: message ?: title
            AppError.Forbidden(detail = mainText, reasonCode = reasonCode)
        } catch (ex: Exception) {
            AppError.Forbidden()
        }
    }

    private fun parseBadRequest(errorBody: String): AppError {
        if (errorBody.isBlank()) return AppError.Validation("Solicitud incorrecta")

        return try {
            val jsonElement = json.parseToJsonElement(errorBody).jsonObject

            val detail = jsonElement["detail"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val message = jsonElement["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val title = jsonElement["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

            val mainMessage = detail ?: message ?: title

            val errorsMap = jsonElement["errors"]?.jsonObject?.mapValues { entry ->
                entry.value.jsonArray.map { it.jsonPrimitive.content }
            }

            if (mainMessage != null || errorsMap != null) {
                AppError.Validation(mainMessage ?: "Error de validación", errorsMap)
            } else {
                AppError.Validation(errorBody)
            }
        } catch (ex: Exception) {
            AppError.Validation(errorBody)
        }
    }
}

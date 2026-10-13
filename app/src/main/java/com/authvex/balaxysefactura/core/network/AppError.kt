package com.authvex.balaxysefactura.core.network

sealed class AppError : Exception() {
    object Network : AppError()
    object Timeout : AppError()
    object Unauthorized : AppError()
    data class Forbidden(val detail: String? = null, val reasonCode: String? = null) : AppError()
    data class NotFound(val detail: String? = null) : AppError()
    data class Conflict(val detail: String? = null) : AppError()
    data class ServerError(val code: Int, val detail: String? = null) : AppError()
    data class Validation(override val message: String, val errors: Map<String, List<String>>? = null) : AppError()
    data class Unexpected(override val message: String) : AppError()

    override val message: String?
        get() = getDisplayMessage()

    fun getDisplayMessage(): String = when (this) {
        is Network -> "No hay conexión a internet."
        is Timeout -> "La conexión ha expirado. Inténtalo de nuevo."
        is Unauthorized -> "Tu sesión ha expirado."
        is Forbidden -> {
            val baseMsg = detail?.takeIf { it.isNotBlank() } ?: "No tienes permisos para realizar esta acción."
            if (!reasonCode.isNullOrBlank() && baseMsg != reasonCode && !baseMsg.contains(reasonCode)) {
                "$baseMsg ($reasonCode)"
            } else {
                baseMsg
            }
        }
        is NotFound -> detail?.takeIf { it.isNotBlank() } ?: "El recurso solicitado no existe."
        is Conflict -> detail?.takeIf { it.isNotBlank() } ?: "Conflicto en la operación."
        is ServerError -> detail?.takeIf { it.isNotBlank() } ?: "Error en el servidor (Código $code)."
        is Validation -> {
            val errorDetails = errors?.entries?.joinToString("\n") { (key, value) -> 
                "$key: ${value.joinToString(", ")}"
            }
            if (!errorDetails.isNullOrBlank()) {
                "$message\n$errorDetails"
            } else {
                message
            }
        }
        is Unexpected -> message
    }
}

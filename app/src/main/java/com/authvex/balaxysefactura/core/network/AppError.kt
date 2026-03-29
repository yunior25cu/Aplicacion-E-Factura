package com.authvex.balaxysefactura.core.network

sealed class AppError : Exception() {
    object Network : AppError()
    object Timeout : AppError()
    object Unauthorized : AppError()
    data class Forbidden(val detail: String? = null) : AppError()
    object NotFound : AppError()
    object Conflict : AppError()
    data class ServerError(val code: Int) : AppError()
    data class Validation(override val message: String, val errors: Map<String, List<String>>? = null) : AppError()
    data class Unexpected(override val message: String) : AppError()

    fun getDisplayMessage(): String = when (this) {
        is Network -> "No hay conexión a internet."
        is Timeout -> "La conexión ha expirado. Inténtalo de nuevo."
        is Unauthorized -> "Tu sesión ha expirado."
        is Forbidden -> detail ?: "No tienes permisos para realizar esta acción."
        is NotFound -> "El recurso solicitado no existe."
        is Conflict -> "Conflicto en la operación."
        is ServerError -> "Error en el servidor (Código $code)."
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

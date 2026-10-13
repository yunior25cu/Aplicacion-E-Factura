package com.authvex.balaxysefactura.core.network

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ErrorMapperTest {

    private fun createHttpException(code: Int, jsonBody: String): HttpException {
        val response = Response.error<Unit>(code, jsonBody.toResponseBody())
        return HttpException(response)
    }

    @Test
    fun `ERROR_MAPPER_409_PROBLEM_DETAILS_PREFERS_DETAIL`() {
        val jsonBody = """
            {
              "type": "https://tools.ietf.org/html/rfc7231#section-6.5.8",
              "title": "Recurso Duplicado",
              "status": 409,
              "detail": "Ya existe un registro cliente con la misma información. Verifica tus datos e inténtalo de nuevo.",
              "instance": "/api/v1/Cliente",
              "traceId": "00-987654321-00"
            }
        """.trimIndent()

        val exception = createHttpException(409, jsonBody)
        val appError = ErrorMapper.fromThrowable(exception)

        assertTrue(appError is AppError.Conflict)
        assertEquals(
            "Ya existe un registro cliente con la misma información. Verifica tus datos e inténtalo de nuevo.",
            appError.getDisplayMessage()
        )
    }

    @Test
    fun `ERROR_MAPPER_PREFERS_DETAIL_OVER_TITLE`() {
        val jsonBody = """
            {
              "title": "Titulo Corto",
              "detail": "Detalle explicativo completo"
            }
        """.trimIndent()

        val exception = createHttpException(409, jsonBody)
        val appError = ErrorMapper.fromThrowable(exception)

        assertEquals("Detalle explicativo completo", appError.getDisplayMessage())
    }

    @Test
    fun `ERROR_MAPPER_PREFERS_MESSAGE_WHEN_DETAIL_MISSING`() {
        val jsonBody = """
            {
              "title": "Titulo Corto",
              "message": "Mensaje explicativo cuando no hay detail"
            }
        """.trimIndent()

        val exception = createHttpException(409, jsonBody)
        val appError = ErrorMapper.fromThrowable(exception)

        assertEquals("Mensaje explicativo cuando no hay detail", appError.getDisplayMessage())
    }

    @Test
    fun `ERROR_MAPPER_USES_TITLE_WHEN_DETAIL_AND_MESSAGE_MISSING`() {
        val jsonBody = """
            {
              "title": "Solo Titulo Disponibilidad"
            }
        """.trimIndent()

        val exception = createHttpException(409, jsonBody)
        val appError = ErrorMapper.fromThrowable(exception)

        assertEquals("Solo Titulo Disponibilidad", appError.getDisplayMessage())
    }

    @Test
    fun `ERROR_MAPPER_409_WITHOUT_USEFUL_BODY_USES_GENERIC_FALLBACK`() {
        val jsonBody = "{}"

        val exception = createHttpException(409, jsonBody)
        val appError = ErrorMapper.fromThrowable(exception)

        assertTrue(appError is AppError.Conflict)
        assertEquals("Conflicto en la operación.", appError.getDisplayMessage())
    }

    @Test
    fun `ERROR_MAPPER_PRESERVES_403_REASON_CODE`() {
        val jsonBody = """
            {
              "message": "Forbidden",
              "code": "PERMISSION_MISSING"
            }
        """.trimIndent()

        val exception = createHttpException(403, jsonBody)
        val appError = ErrorMapper.fromThrowable(exception)

        assertTrue(appError is AppError.Forbidden)
        assertEquals("Forbidden (PERMISSION_MISSING)", appError.getDisplayMessage())
    }

    @Test
    fun `ERROR_MAPPER_DOES_NOT_EXPOSE_TRACE_ID`() {
        val jsonBody = """
            {
              "title": "Error Servidor",
              "detail": "Detalle para usuario",
              "traceId": "00-SECRET-TRACE-ID-999"
            }
        """.trimIndent()

        val exception = createHttpException(409, jsonBody)
        val appError = ErrorMapper.fromThrowable(exception)

        val message = appError.getDisplayMessage()
        assertFalse(message.contains("00-SECRET-TRACE-ID-999"))
        assertFalse(message.contains("traceId"))
        assertEquals("Detalle para usuario", message)
    }
}

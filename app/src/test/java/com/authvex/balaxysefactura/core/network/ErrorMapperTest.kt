package com.authvex.balaxysefactura.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ErrorMapperTest {

    @Test
    fun `fromHttpException 401 should return Unauthorized`() {
        val exception = createHttpException(401, "")
        val result = ErrorMapper.fromThrowable(exception)
        assertTrue(result is AppError.Unauthorized)
    }

    @Test
    fun `fromHttpException 400 with ProblemDetails should return Validation`() {
        val json = """{"detail": "Error desde el backend"}"""
        val exception = createHttpException(400, json)
        val result = ErrorMapper.fromThrowable(exception)
        assertTrue(result is AppError.Validation)
        assertEquals("Error desde el backend", (result as AppError.Validation).message)
    }

    @Test
    fun `fromHttpException 400 with ModelState should return Validation with errors`() {
        val json = """{
            "message": "Validación fallida",
            "errors": {
                "Email": ["Formato incorrecto"],
                "Password": ["Muy corta"]
            }
        }"""
        val exception = createHttpException(400, json)
        val result = ErrorMapper.fromThrowable(exception)
        assertTrue(result is AppError.Validation)
        val validation = result as AppError.Validation
        assertEquals("Validación fallida", validation.message)
        assertEquals("Formato incorrecto", validation.errors?.get("Email")?.get(0))
    }

    @Test
    fun `fromHttpException 400 with plain string should return Validation`() {
        val plainText = "Error de servidor legacy en texto plano"
        val exception = createHttpException(400, plainText)
        val result = ErrorMapper.fromThrowable(exception)
        assertTrue(result is AppError.Validation)
        assertEquals(plainText, (result as AppError.Validation).message)
    }

    private fun createHttpException(code: Int, body: String): HttpException {
        val response = Response.error<Any>(code, body.toResponseBody("application/json".toMediaType()))
        return HttpException(response)
    }
}

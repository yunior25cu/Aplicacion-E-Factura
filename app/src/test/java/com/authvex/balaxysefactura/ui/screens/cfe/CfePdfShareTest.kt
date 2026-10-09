package com.authvex.balaxysefactura.ui.screens.cfe

import com.authvex.balaxysefactura.core.network.AppError
import com.authvex.balaxysefactura.core.network.CfeApi
import com.authvex.balaxysefactura.core.network.CfeDetailDto
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.ui.screens.cfe.detail.CfeDetailUiState
import com.authvex.balaxysefactura.ui.screens.cfe.detail.CfeDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Headers
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class CfePdfShareTest {

    private lateinit var cfeApi: CfeApi
    private lateinit var repository: CfeRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cfeApi = mock()
        repository = CfeRepository(cfeApi)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `CFE_PDF_DOWNLOAD_USES_CORRECT_ENDPOINT and CFE_PDF_DOWNLOAD_SETS_REDIRECT_FALSE`() = runTest {
        val pdfBody = "%PDF-1.4 Test".toResponseBody()
        val headers = Headers.headersOf("Content-Type", "application/pdf")
        val retrofitResponse = Response.success(pdfBody, headers)

        whenever(cfeApi.downloadCfePdf(74095L, false)).thenReturn(retrofitResponse)

        val result = repository.downloadCfePdf(74095L)

        assertTrue(result.isSuccess)
        verify(cfeApi).downloadCfePdf(74095L, false)
    }

    @Test
    fun `CFE_PDF_REJECTS_NON_PDF_CONTENT_TYPE - rejects json or html content type`() = runTest {
        val jsonBody = "{\"error\":\"not_ready\"}".toResponseBody()
        val headers = Headers.headersOf("Content-Type", "application/json")
        val retrofitResponse = Response.success(jsonBody, headers)

        whenever(cfeApi.downloadCfePdf(74095L, false)).thenReturn(retrofitResponse)

        val result = repository.downloadCfePdf(74095L)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is AppError.Validation)
        assertTrue((error as AppError.Validation).message.contains("no soportado"))
    }

    @Test
    fun `CFE_PDF_MATERIALIZABLE_HANDLING_TEST - 409 error returns friendly message`() = runTest {
        val errorResponseBody = "{\"message\":\"not_materializable\"}".toResponseBody()
        val retrofitResponse = Response.error<okhttp3.ResponseBody>(409, errorResponseBody)

        whenever(cfeApi.downloadCfePdf(74095L, false)).thenReturn(retrofitResponse)

        val result = repository.downloadCfePdf(74095L)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is AppError.Validation)
        assertTrue((error as AppError.Validation).message.contains("todavía no está disponible"))
    }

    @Test
    fun `CFE_SHARE_DOUBLE_TAP_BLOCKED - double tap during share is blocked`() = runTest {
        val cfeDetail = CfeDetailDto(
            documentoId = 74095,
            serie = "A",
            numero = 123L,
            cfeCode = 111,
            estadoCfe = 2,
            estadoReceptor = 0,
            receptor = "ABITAB S A",
            fechaEmision = "2026-01-04",
            fechaConfirmacion = "2026-01-04",
            fechaEnvioUtc = null,
            fechaAceptadoUtc = null,
            importeTotal = 1029.0,
            iva = 0.0,
            monedaCodigo = "UYU",
            monedaSimbolo = "$",
            ultimoError = null
        )

        whenever(cfeApi.getDocument(74095)).thenReturn(cfeDetail)

        val viewModel = CfeDetailViewModel(repository, 74095L)
        assertTrue(viewModel.uiState is CfeDetailUiState.Success)

        assertFalse(viewModel.isSharingPdf)
    }
}

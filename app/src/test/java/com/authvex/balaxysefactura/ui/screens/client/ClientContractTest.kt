package com.authvex.balaxysefactura.ui.screens.client

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.ClientRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ClientContractTest {

    private lateinit var clientApi: ClientApi
    private lateinit var clientRepository: ClientRepository
    private val testDispatcher = UnconfinedTestDispatcher()
    private val prodJson = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        clientApi = mock()
        clientRepository = ClientRepository(clientApi)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `CLIENT_RUC_SERIALIZES_TYPE_2_AND_EMPRESA_TRUE`() {
        val req = ClientCreateRequest(
            denominacion = "ABITAB S A",
            codigo = "218889990011",
            tipoDocumentoIdentificacion = ClientDocumentType.RUC.apiValue,
            esEmpresa = true
        )

        val serialized = prodJson.encodeToString(req)
        val jsonObject = prodJson.parseToJsonElement(serialized).jsonObject

        assertEquals(2, ClientDocumentType.RUC.apiValue)
        assertEquals(2, jsonObject["tipoDocumentoIdentificacion"]?.toString()?.toInt())
        assertEquals("true", jsonObject["esEmpresa"]?.toString())
        assertFalse(serialized.contains("idEmpresa"))
        assertFalse(serialized.contains("idUsuario"))
    }

    @Test
    fun `CLIENT_CI_SERIALIZES_TYPE_3_AND_EMPRESA_FALSE`() {
        val req = ClientCreateRequest(
            denominacion = "JUAN PEREZ",
            codigo = "12345678",
            tipoDocumentoIdentificacion = ClientDocumentType.CI.apiValue,
            esEmpresa = false
        )

        val serialized = prodJson.encodeToString(req)
        val jsonObject = prodJson.parseToJsonElement(serialized).jsonObject

        assertEquals(3, ClientDocumentType.CI.apiValue)
        assertEquals(3, jsonObject["tipoDocumentoIdentificacion"]?.toString()?.toInt())
        assertEquals("false", jsonObject["esEmpresa"]?.toString())
    }

    @Test
    fun `CLIENT_OTHER_SERIALIZES_TYPE_4_AND_EMPRESA_FALSE`() {
        assertEquals(4, ClientDocumentType.OTHER.apiValue)
    }

    @Test
    fun `CLIENT_CREATE_EXPECTS_204_NO_CONTENT`() = runTest {
        val req = ClientCreateRequest(
            denominacion = "TEST CLIENT",
            codigo = "218889990011",
            tipoDocumentoIdentificacion = 2,
            esEmpresa = true
        )

        whenever(clientApi.createClient(any())).thenReturn(Response.success(204, Unit))

        val result = clientRepository.createClient(req)

        assertTrue(result.isSuccess)
        verify(clientApi).createClient(eq(req))
    }

    @Test
    fun `CLIENT_RUC_AND_CI_VALIDATIONS`() {
        val viewModel = ClientCreateViewModel(clientRepository)

        // RUC validation
        viewModel.onDocTypeChanged(ClientDocumentType.RUC)
        viewModel.onDocumentNumberChanged("218889990011a-")
        assertEquals("218889990011", viewModel.documentNumber) // Strips non-digits
        assertTrue(viewModel.isDgiQueryAvailable())

        // CI validation
        viewModel.onDocTypeChanged(ClientDocumentType.CI)
        viewModel.onDocumentNumberChanged("12345678x")
        assertEquals("12345678", viewModel.documentNumber) // Strips non-digits
        assertFalse(viewModel.isDgiQueryAvailable())
    }

    @Test
    fun `CLIENT_DGI_RUC_FLAG_OK_PREFILLS_FIELDS`() = runTest {
        val dgiResponse = DgiPersonaActEmpresarialResponseDto(
            flag = "OK",
            rut = "218889990011",
            denominacion = "ABITAB S.A.",
            direccionFiscal = "Av. Italia 1234",
            ciudad = "Montevideo",
            departamento = "Montevideo",
            email = "contacto@abitab.com.uy",
            telefono = "29001122"
        )

        whenever(clientApi.getThirdPartyRegistralData("218889990011")).thenReturn(dgiResponse)

        val viewModel = ClientCreateViewModel(clientRepository)
        viewModel.onDocTypeChanged(ClientDocumentType.RUC)
        viewModel.onDocumentNumberChanged("218889990011")

        viewModel.queryDgi()

        assertEquals("ABITAB S.A.", viewModel.denominacion)
        assertEquals("Av. Italia 1234", viewModel.direccion)
        assertEquals("Montevideo", viewModel.ciudad)
        assertEquals("Montevideo", viewModel.departamento)
        assertEquals("contacto@abitab.com.uy", viewModel.email)
        assertEquals("29001122", viewModel.telefono)
        assertEquals("Datos autocompletados desde DGI", viewModel.dgiMessage)
    }

    @Test
    fun `CLIENT_DGI_FAILURE_DOES_NOT_BLOCK_MANUAL_CREATE`() = runTest {
        whenever(clientApi.getThirdPartyRegistralData("218889990011"))
            .thenReturn(DgiPersonaActEmpresarialResponseDto(flag = "ERROR", mensaje = "RUT no encontrado"))

        val viewModel = ClientCreateViewModel(clientRepository)
        viewModel.onDocTypeChanged(ClientDocumentType.RUC)
        viewModel.onDocumentNumberChanged("218889990011")

        viewModel.queryDgi()

        assertEquals("RUT no encontrado", viewModel.dgiMessage)
        assertFalse(viewModel.isQueryingDgi)

        // Manual fill and save continues
        viewModel.denominacion = "MANUAL CLIENT"
        whenever(clientApi.createClient(any())).thenReturn(Response.success(204, Unit))

        viewModel.saveClient()

        assertTrue(viewModel.uiState is ClientCreateUiState.Success)
        verify(clientApi).createClient(any())
    }
}

package com.authvex.balaxysefactura.ui.screens.cfe.detail

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.core.repository.CollectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PostEmissionUxTest {

    private lateinit var cfeRepository: CfeRepository
    private lateinit var collectionRepository: CollectionRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cfeRepository = mock()
        collectionRepository = mock()

        runTest {
            val empresa = EmpresaDto(id = 10, nombre = "Empresa", moneda = CatalogoItemDto(50, "Pesos", "UYU"))
            whenever(cfeRepository.getEmpresa()).thenReturn(Result.success(empresa))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `POST_EMISSION_DETAIL_REUSES_EXISTING_DOCUMENT_ID`() = runTest {
        val cfeDetail = CfeDetailDto(
            documentoId = 22020,
            serie = "A",
            numero = 22020L,
            cfeCode = 111,
            receptor = "ABITAB S A",
            importeTotal = 1220.0
        )

        whenever(cfeRepository.getDocumentDetail(22020)).thenReturn(Result.success(cfeDetail))

        val viewModel = CfeDetailViewModel(cfeRepository, 22020L, collectionRepository)

        assertTrue(viewModel.uiState is CfeDetailUiState.Success)
        val state = viewModel.uiState as CfeDetailUiState.Success
        assertEquals(22020, state.document.documentoId)
        assertEquals("ABITAB S A", state.document.receptor)
        assertEquals(1220.0, state.document.importeTotal!!, 0.001)
    }

    @Test
    fun `POST_EMISSION_PAYMENT_STATUS_VISIBLE_FOR_COLLECTABLE_CFE`() = runTest {
        val cfeDetail = CfeDetailDto(
            documentoId = 22020,
            serie = "A",
            numero = 22020L,
            cfeCode = 111, // e-Factura
            receptor = "ABITAB S A",
            importeTotal = 1220.0
        )

        val invoice = CollectionInvoiceDto(
            id = 22020L,
            estado = 2,
            importeTotalBase = 1220.0,
            importeTotalBaseCobrado = 400.0 // Partial payment
        )

        whenever(cfeRepository.getDocumentDetail(22020)).thenReturn(Result.success(cfeDetail))
        whenever(collectionRepository.getInvoiceById(22020L)).thenReturn(Result.success(invoice))

        val viewModel = CfeDetailViewModel(cfeRepository, 22020L, collectionRepository)

        assertNotNull(viewModel.linkedFactura)
        assertEquals(22020L, viewModel.linkedFactura!!.id)

        val (pendingBase, pendingOrig) = viewModel.linkedFactura!!.calculatePendingBalances()
        assertEquals(820.0, pendingBase, 0.001) // 1220 - 400 = 820 pending
    }

    @Test
    fun `EXISTING_CFE_DETAIL_REGRESSION_IS_NO`() = runTest {
        val cfeDetail = CfeDetailDto(
            documentoId = 100,
            serie = "A",
            numero = 100L,
            cfeCode = 101, // e-Ticket
            receptor = "CLIENTE REGRESSION",
            importeTotal = 500.0
        )

        whenever(cfeRepository.getDocumentDetail(100)).thenReturn(Result.success(cfeDetail))

        val viewModel = CfeDetailViewModel(cfeRepository, 100L, collectionRepository)

        assertTrue(viewModel.uiState is CfeDetailUiState.Success)
        val state = viewModel.uiState as CfeDetailUiState.Success
        assertEquals("CLIENTE REGRESSION", state.document.receptor)
    }
}

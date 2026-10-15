package com.authvex.balaxysefactura.ui.screens.emission

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class EmissionStockTest {

    private lateinit var cfeRepository: CfeRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cfeRepository = mock()

        runTest {
            val pv = PuntoVentaDto(id = 1, nombre = "PV1", numero = 1, activo = true, esPredeterminado = true)
            val group = CfeFiscalDocumentAvailabilityGroupDto(
                puntoVentaId = 1,
                items = listOf(CfeFiscalDocumentAvailabilityItemDto(cfeCode = 111, name = "e-Factura", habilitado = true, puntoVentaId = 1, serie = "A", numeroActual = 100L))
            )
            whenever(cfeRepository.getPuntosVenta()).thenReturn(Result.success(listOf(pv)))
            whenever(cfeRepository.getDocumentosHabilitados(1)).thenReturn(Result.success(listOf(group)))
            whenever(cfeRepository.getTasaCambios(any())).thenReturn(Result.success(listOf(TasaCambioSimpleDto(50, "UYU", "Pesos", "$", 2, 1.0))))
            whenever(cfeRepository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(5, "Almacén Central"))))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `PRODUCT_EXISTENCE_USES_PRODUCTO_EXISTENCIA_ENDPOINT_SENDS_ID_WAREHOUSE_AND_DATE`() = runTest {
        val physicalProd = ProductoDto(id = 55, nombre = "Mesa Madera", esServicio = false, precio = 1000.0)
        val stockDto = ProductoExistenciaDto(existencia = 297.0, costo = 500.0)

        whenever(cfeRepository.getProductoExistencia(eq(55L), eq(5L), any(), eq(null)))
            .thenReturn(Result.success(stockDto))

        val viewModel = EmissionViewModel(cfeRepository)
        viewModel.selectedPOS = PuntoVentaDto(1, "PV1", 1, true, true)
        viewModel.selectedFiscalType = CfeFiscalDocumentAvailabilityItemDto(cfeCode = 111, name = "e-Factura", habilitado = true, puntoVentaId = 1, serie = "A")
        viewModel.selectedAlmacen = CatalogoItemDto(5, "Almacén Central")

        viewModel.startLineConfiguration(physicalProd)

        verify(cfeRepository).getProductoExistencia(
            idProducto = eq(55L),
            idAlmacen = eq(5L),
            fecha = eq(viewModel.fechaConfirmacion),
            idSkuVariante = eq(null)
        )

        assertFalse(viewModel.lineStockLoading)
        assertFalse(viewModel.lineStockError)
        assertEquals(297.0, viewModel.lineStockExistence!!, 0.001)
    }

    @Test
    fun `SERVICE_DOES_NOT_REQUEST_PRODUCT_EXISTENCE`() = runTest {
        val serviceProd = ProductoDto(id = 88, nombre = "Asesoría Legal", esServicio = true, precio = 500.0)

        val viewModel = EmissionViewModel(cfeRepository)
        viewModel.selectedPOS = PuntoVentaDto(1, "PV1", 1, true, true)
        viewModel.selectedFiscalType = CfeFiscalDocumentAvailabilityItemDto(cfeCode = 111, name = "e-Factura", habilitado = true, puntoVentaId = 1, serie = "A")
        viewModel.selectedAlmacen = CatalogoItemDto(5, "Almacén Central")

        viewModel.startLineConfiguration(serviceProd)

        verify(cfeRepository, never()).getProductoExistencia(any(), any(), any(), any())
        assertNull(viewModel.lineStockExistence)
        assertFalse(viewModel.lineStockLoading)
    }

    @Test
    fun `PRODUCT_EXISTENCE_FAILURE_DOES_NOT_BLOCK_LINE_CONFIRM_OR_EMISSION`() = runTest {
        val physicalProd = ProductoDto(id = 55, nombre = "Mesa Madera", esServicio = false, precio = 1000.0)

        whenever(cfeRepository.getProductoExistencia(eq(55L), eq(5L), any(), eq(null)))
            .thenReturn(Result.failure(Exception("Stock service unavailable")))

        val viewModel = EmissionViewModel(cfeRepository)
        viewModel.selectedPOS = PuntoVentaDto(1, "PV1", 1, true, true)
        viewModel.selectedFiscalType = CfeFiscalDocumentAvailabilityItemDto(cfeCode = 111, name = "e-Factura", habilitado = true, puntoVentaId = 1, serie = "A")
        viewModel.selectedAlmacen = CatalogoItemDto(5, "Almacén Central")

        viewModel.startLineConfiguration(physicalProd)

        assertTrue(viewModel.lineStockError)
        assertNull(viewModel.lineStockExistence)

        // Line confirm succeeds despite stock failure
        viewModel.confirmLineConfiguration(cantidad = 2.0, precio = 1000.0, indicadorC4 = null)

        assertEquals(1, viewModel.lineas.size)
        assertEquals(2.0, viewModel.lineas.first().cantidad, 0.001)
        assertEquals(1000.0, viewModel.lineas.first().precioUnitario, 0.001)
    }

    @Test
    fun `PRODUCT_EXISTENCE_ZERO_DOES_NOT_BLOCK_LINE_CONFIRM`() = runTest {
        val physicalProd = ProductoDto(id = 55, nombre = "Mesa Madera", esServicio = false, precio = 1000.0)
        val stockDto = ProductoExistenciaDto(existencia = 0.0)

        whenever(cfeRepository.getProductoExistencia(eq(55L), eq(5L), any(), eq(null)))
            .thenReturn(Result.success(stockDto))

        val viewModel = EmissionViewModel(cfeRepository)
        viewModel.selectedPOS = PuntoVentaDto(1, "PV1", 1, true, true)
        viewModel.selectedFiscalType = CfeFiscalDocumentAvailabilityItemDto(cfeCode = 111, name = "e-Factura", habilitado = true, puntoVentaId = 1, serie = "A")
        viewModel.selectedAlmacen = CatalogoItemDto(5, "Almacén Central")

        viewModel.startLineConfiguration(physicalProd)

        assertEquals(0.0, viewModel.lineStockExistence!!, 0.001)

        viewModel.confirmLineConfiguration(cantidad = 5.0, precio = 1000.0, indicadorC4 = null)

        assertEquals(1, viewModel.lineas.size)
        assertEquals(5.0, viewModel.lineas.first().cantidad, 0.001)
    }
}

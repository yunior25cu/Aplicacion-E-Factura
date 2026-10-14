package com.authvex.balaxysefactura.ui.screens.product

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.CfeRepository
import com.authvex.balaxysefactura.core.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ProductContractTest {

    private lateinit var productApi: ProductApi
    private lateinit var productRepository: ProductRepository
    private lateinit var cfeRepository: CfeRepository
    private val testDispatcher = UnconfinedTestDispatcher()
    private val prodJson = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        productApi = mock()
        productRepository = ProductRepository(productApi)
        cfeRepository = mock()

        runTest {
            val empresaConfig = EmpresaConfigDto(idInventarioTipoCuenta = 1001L)
            val empresa = EmpresaDto(id = 10, nombre = "Empresa", empresaConfig = empresaConfig)
            whenever(cfeRepository.getEmpresa()).thenReturn(Result.success(empresa))
            whenever(productRepository.getUnidadesMedida()).thenReturn(Result.success(listOf(CatalogoItemDto(1, "Unidad", "UN"))))
            whenever(productRepository.getTiposProducto()).thenReturn(Result.success(listOf(CatalogoItemDto(2, "Mercadería", "MERC"))))
            whenever(cfeRepository.getAlmacenes()).thenReturn(Result.success(listOf(CatalogoItemDto(3, "Principal"))))
            whenever(productRepository.getImpuestosVenta()).thenReturn(Result.success(listOf(CatalogoItemDto(10, "IVA 22%", "22"))))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `PRODUCT_TAX_OPTIONS_USE_RATE_NOT_DUPLICATED_DENOMINATION`() {
        val tax10 = CatalogoItemDto(id = 1, nombre = "IVA Ventas", importe = 0.10)
        val tax22 = CatalogoItemDto(id = 2, nombre = "IVA Ventas", importe = 0.22)
        val tax0 = CatalogoItemDto(id = 3, nombre = "IVA Ventas", importe = 0.00)

        assertEquals("IVA 10%", tax10.getTaxLabel())
        assertEquals("IVA 22%", tax22.getTaxLabel())
        assertEquals("IVA 0%", tax0.getTaxLabel())

        // Distinct tax IDs are preserved
        val taxes = listOf(tax10, tax22, tax0)
        assertEquals(3, taxes.size)
        assertEquals(listOf(1, 2, 3), taxes.map { it.id })
    }

    @Test
    fun `PRODUCT_CREATE_JSON_ALWAYS_CONTAINS_COST_ZERO`() {
        val req = ProductCreateRequest(
            codigo = "SKU-99",
            denominacion = "Producto Con Costo Cero",
            esServicio = false,
            precioVenta = 100.0,
            modoPrecioVenta = 0,
            costo = 0.0,
            fechaEntrada = "2026-10-10",
            idTipoProducto = 1L,
            idUnidadMedida = 1L,
            idTipoCuenta = 1001L
        )

        val jsonString = prodJson.encodeToString(req)
        val jsonObject = prodJson.parseToJsonElement(jsonString).jsonObject

        // MANDATORY: "costo" must be present in JSON even when 0.0!
        assertTrue(jsonObject.containsKey("costo"))
        assertEquals("0.0", jsonObject["costo"]?.jsonPrimitive?.content)
    }

    @Test
    fun `PRODUCT_CREATE_SERIALIZES_REQUIRED_FIELDS_AND_OMITS_USER_AND_TENANT_IDS`() {
        val req = ProductCreateRequest(
            codigo = "SKU-100",
            denominacion = "Producto Prueba",
            esServicio = false,
            precioVenta = 150.0,
            modoPrecioVenta = 0,
            costo = 0.0,
            fechaEntrada = "2026-10-10",
            idTipoProducto = 2L,
            idUnidadMedida = 1L,
            idTipoCuenta = 1001L,
            almacenProductos = listOf(AlmacenProductoRequest(3L))
        )

        val serialized = prodJson.encodeToString(req)
        val jsonObject = prodJson.parseToJsonElement(serialized).jsonObject

        assertEquals("SKU-100", jsonObject["codigo"]?.jsonPrimitive?.content)
        assertEquals("Producto Prueba", jsonObject["denominacion"]?.jsonPrimitive?.content)
        assertEquals(0, jsonObject["modoPrecioVenta"]?.jsonPrimitive?.int)
        assertEquals("0.0", jsonObject["costo"]?.jsonPrimitive?.content)
        assertEquals("false", jsonObject["esServicio"]?.jsonPrimitive?.content)

        assertFalse(serialized.contains("idEmpresa"))
        assertFalse(serialized.contains("idUsuario"))
    }

    @Test
    fun `SERVICE_SERIALIZES_ES_SERVICIO_TRUE_AND_GROSS_PRICE_MODE_1`() {
        val req = ProductCreateRequest(
            codigo = "SERV-200",
            denominacion = "Servicio Asesoría",
            esServicio = true,
            precioVenta = 500.0,
            modoPrecioVenta = 1,
            precioVentaConIva = 500.0,
            costo = 0.0,
            fechaEntrada = "2026-10-10",
            idTipoProducto = 2L,
            idUnidadMedida = 1L,
            idTipoCuenta = 1001L,
            almacenProductos = emptyList()
        )

        val serialized = prodJson.encodeToString(req)
        val jsonObject = prodJson.parseToJsonElement(serialized).jsonObject

        assertTrue(jsonObject["esServicio"]?.jsonPrimitive?.content?.toBoolean()!!)
        assertEquals(1, jsonObject["modoPrecioVenta"]?.jsonPrimitive?.int)
        assertEquals(500.0, jsonObject["precioVentaConIva"]?.jsonPrimitive?.content?.toDouble()!!, 0.001)
    }

    @Test
    fun `PRODUCT_TYPE_AND_UNIT_ARE_PRESELECTED_BY_DEFAULT`() = runTest {
        val unidad = CatalogoItemDto(id = 1, nombre = "Unidad", codigo = "UN")
        val tipoMerc = CatalogoItemDto(id = 2, nombre = "Para la Venta o Insumo", codigo = "MERC")
        val tipoOtro = CatalogoItemDto(id = 3, nombre = "Materia Prima", codigo = "MAT")

        whenever(productRepository.getUnidadesMedida()).thenReturn(Result.success(listOf(unidad)))
        whenever(productRepository.getTiposProducto()).thenReturn(Result.success(listOf(tipoOtro, tipoMerc)))

        val viewModel = ProductCreateViewModel(productRepository, cfeRepository)
        viewModel.loadCatalogs()

        assertNotNull(viewModel.selectedUnidadMedida)
        assertEquals(1, viewModel.selectedUnidadMedida?.id)

        assertNotNull(viewModel.selectedTipoProducto)
        assertEquals(2, viewModel.selectedTipoProducto?.id)
    }

    @Test
    fun `PRODUCT_CREATE_USES_POST_PRODUCTO_AND_RETURNS_SERVER_ID`() = runTest {
        val req = ProductCreateRequest(
            codigo = "SKU-100",
            denominacion = "Producto Prueba",
            esServicio = false,
            precioVenta = 100.0,
            modoPrecioVenta = 0,
            fechaEntrada = "2026-10-10",
            idTipoProducto = 2L,
            idUnidadMedida = 1L,
            idTipoCuenta = 1001L
        )

        whenever(productApi.createProduct(any())).thenReturn(9999L)

        val result = productRepository.createProduct(req)

        assertTrue(result.isSuccess)
        assertEquals(9999L, result.getOrNull())
        verify(productApi).createProduct(eq(req))
    }

    @Test
    fun `PRODUCT_GET_CREATED_USES_PRODUCTO_ID`() = runTest {
        val createdProd = ProductoDto(id = 9999, nombre = "Producto Creado", codigo = "SKU-100", precio = 100.0)

        whenever(productApi.getProductById(9999L)).thenReturn(createdProd)

        val result = productRepository.getProductById(9999L)

        assertTrue(result.isSuccess)
        assertEquals("Producto Creado", result.getOrNull()?.nombre)
        verify(productApi).getProductById(eq(9999L))
    }

    @Test
    fun `PRODUCT_VALIDATIONS_NAME_SKU_PRICE_WAREHOUSE_ACCOUNT`() = runTest {
        val viewModel = ProductCreateViewModel(productRepository, cfeRepository)
        viewModel.loadCatalogs()

        // 1. Physical product with zero price -> Error
        viewModel.isService = false
        viewModel.denominacion = "Mesa"
        viewModel.codigo = "SKU-001"
        viewModel.precioVentaText = "0.0"

        viewModel.saveProduct()
        assertTrue(viewModel.uiState is ProductCreateUiState.Error)
        val err1 = viewModel.uiState as ProductCreateUiState.Error
        assertTrue(err1.message.contains("mayor a cero"))

        // 2. Service product with zero price -> Allowed
        viewModel.isService = true
        viewModel.precioVentaText = "0.0"

        val createdDto = ProductoDto(id = 555, nombre = "Mesa", codigo = "SKU-001", precio = 0.0)
        whenever(productApi.createProduct(any())).thenReturn(555L)
        whenever(productApi.getProductById(555L)).thenReturn(createdDto)

        viewModel.saveProduct()
        assertTrue(viewModel.uiState is ProductCreateUiState.Success)
        assertEquals(555, (viewModel.uiState as ProductCreateUiState.Success).createdProduct.id)
    }
}

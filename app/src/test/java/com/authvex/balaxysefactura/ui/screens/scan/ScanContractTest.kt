package com.authvex.balaxysefactura.ui.screens.scan

import com.authvex.balaxysefactura.core.network.*
import com.authvex.balaxysefactura.core.repository.BlobUploader
import com.authvex.balaxysefactura.core.repository.ScanRepository
import com.authvex.balaxysefactura.ui.navigation.Screen
import com.authvex.balaxysefactura.ui.screens.scan.inbox.ScanInboxUiState
import com.authvex.balaxysefactura.ui.screens.scan.inbox.ScanInboxViewModel
import com.authvex.balaxysefactura.ui.screens.scan.inbox.ScanStatusFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ScanContractTest {

    private lateinit var scanApi: ScanApi
    private lateinit var blobUploader: BlobUploader
    private lateinit var scanRepository: ScanRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scanApi = mock()
        blobUploader = mock()
        scanRepository = ScanRepository(scanApi, blobUploader)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `SCAN_TARGET_SALE_SERIALIZES_AS_2 and PURCHASE_SERIALIZES_AS_1`() {
        assertEquals(2, ScanTarget.SALE.apiValue)
        assertEquals(1, ScanTarget.PURCHASE.apiValue)
    }

    @Test
    fun `SCAN_NAVIGATION_ROUTE_EXISTS`() {
        assertEquals("scan", Screen.Scan.route)
        assertEquals("scan_status/88", Screen.ScanStatus.createRoute(88L))
        assertEquals("scan_inbox", Screen.ScanInbox.route)
    }

    @Test
    fun `SCAN_INBOX_USES_OCR_MOBILE_INBOX_ENDPOINT_AND_TARGET_FILTERS`() = runTest {
        val item1 = ScanInboxItemDto(documentId = 100L, target = 2, ingestionStatus = 4, number = "A-123", partnerName = "Cliente A")
        val item2 = ScanInboxItemDto(documentId = 200L, target = 1, ingestionStatus = 5, number = "B-456", partnerName = "Proveedor B")

        whenever(scanApi.getInbox(null)).thenReturn(listOf(item1, item2))
        whenever(scanApi.getInbox(2)).thenReturn(listOf(item1))
        whenever(scanApi.getInbox(1)).thenReturn(listOf(item2))

        val viewModel = ScanInboxViewModel(scanRepository)

        // All target
        assertTrue(viewModel.uiState is ScanInboxUiState.Success)
        val allState = viewModel.uiState as ScanInboxUiState.Success
        assertEquals(2, allState.items.size)

        // Filter Sales target = 2
        viewModel.onTargetFilterSelected(ScanTarget.SALE)
        verify(scanApi).getInbox(eq(2))
        val salesState = viewModel.uiState as ScanInboxUiState.Success
        assertEquals(1, salesState.items.size)
        assertEquals(100L, salesState.items.first().documentId)

        // Filter Purchases target = 1
        viewModel.onTargetFilterSelected(ScanTarget.PURCHASE)
        verify(scanApi).getInbox(eq(1))
        val purchasesState = viewModel.uiState as ScanInboxUiState.Success
        assertEquals(1, purchasesState.items.size)
        assertEquals(200L, purchasesState.items.first().documentId)
    }

    @Test
    fun `SCAN_INBOX_STATUS_FILTERING_PROCESSING_READY_ERROR`() = runTest {
        val itemProcessing = ScanInboxItemDto(documentId = 1L, ingestionStatus = 4) // Processing
        val itemReady = ScanInboxItemDto(documentId = 2L, ingestionStatus = 5) // ReadyForReview
        val itemError = ScanInboxItemDto(documentId = 3L, ingestionStatus = 6) // Error

        whenever(scanApi.getInbox(null)).thenReturn(listOf(itemProcessing, itemReady, itemError))

        val viewModel = ScanInboxViewModel(scanRepository)

        // All status
        val allState = viewModel.uiState as ScanInboxUiState.Success
        assertEquals(3, allState.items.size)

        // Filter Processing (1..4)
        viewModel.onStatusFilterSelected(ScanStatusFilter.PROCESSING)
        val procState = viewModel.uiState as ScanInboxUiState.Success
        assertEquals(1, procState.items.size)
        assertEquals(1L, procState.items.first().documentId)

        // Filter Ready (5)
        viewModel.onStatusFilterSelected(ScanStatusFilter.READY)
        val readyState = viewModel.uiState as ScanInboxUiState.Success
        assertEquals(1, readyState.items.size)
        assertEquals(2L, readyState.items.first().documentId)

        // Filter Error (6)
        viewModel.onStatusFilterSelected(ScanStatusFilter.ERROR)
        val errState = viewModel.uiState as ScanInboxUiState.Success
        assertEquals(1, errState.items.size)
        assertEquals(3L, errState.items.first().documentId)
    }

    @Test
    fun `SCAN_INIT_AND_COMPLETE_AND_STATUS_ENDPOINTS_FLOW`() = runTest {
        val tempFile = File.createTempFile("scan_test", ".pdf")
        tempFile.writeBytes(ByteArray(1024)) // 1 KB

        val initRes = ScanInitUploadResponse(
            documentId = 12345L,
            uploadUrl = "https://balaxys.blob.core.windows.net/container/file.pdf?sas=123",
            blobPath = "container/file.pdf",
            uploadExpiresAt = "2026-10-10"
        )

        whenever(scanApi.initUpload(any())).thenReturn(initRes)
        whenever(blobUploader.uploadToAzureBlob(any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(scanApi.completeUpload(any())).thenReturn(ScanStatusDto(documentId = 12345L, ingestionStatus = 1))

        val uploadResult = scanRepository.uploadScannedDocument(tempFile, "application/pdf", ScanTarget.SALE)

        assertTrue(uploadResult.isSuccess)
        assertEquals(12345L, uploadResult.getOrNull())

        verify(scanApi).initUpload(org.mockito.kotlin.argThat {
            contentType == "application/pdf" && target == 2
        })

        verify(blobUploader).uploadToAzureBlob(
            uploadUrl = eq("https://balaxys.blob.core.windows.net/container/file.pdf?sas=123"),
            file = eq(tempFile),
            contentType = eq("application/pdf")
        )

        verify(scanApi).completeUpload(org.mockito.kotlin.argThat {
            documentId == 12345L
        })
    }

    @Test
    fun `BLOB_UPLOAD_DOES_NOT_SEND_BALAXYS_AUTHORIZATION_HEADER`() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.start()

        val sasUrl = mockWebServer.url("/test-blob.pdf").toString()
        val tempFile = File.createTempFile("blob_test", ".pdf")
        tempFile.writeText("test data")

        val realBlobUploader = BlobUploader()
        val result = realBlobUploader.uploadToAzureBlob(sasUrl, tempFile, "application/pdf")

        assertTrue(result.isSuccess)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("PUT", recordedRequest.method)
        assertEquals("BlockBlob", recordedRequest.getHeader("x-ms-blob-type"))
        assertEquals("application/pdf", recordedRequest.getHeader("Content-Type"))

        // MANDATORY: Authorization header MUST NOT be sent to Azure SAS!
        assertNull(recordedRequest.getHeader("Authorization"))

        mockWebServer.shutdown()
    }

    @Test
    fun `SCAN_REJECTS_FILE_OVER_25_MB`() = runTest {
        val viewModel = ScanViewModel(scanRepository)

        val largeFile = mock<File>()
        whenever(largeFile.length()).thenReturn(26L * 1024 * 1024) // 26 MB
        whenever(largeFile.name).thenReturn("large_document.pdf")

        viewModel.onDocumentScanned(largeFile)
        viewModel.uploadDocument()

        assertTrue(viewModel.uiState is ScanUiState.Error)
        val err = viewModel.uiState as ScanUiState.Error
        assertTrue(err.message.contains("25 MB"))
    }

    @Test
    fun `SCAN_MIME_TYPE_VALIDATION`() {
        val viewModel = ScanViewModel(scanRepository)

        assertEquals("application/pdf", viewModel.getMimeType(File("test.pdf")))
        assertEquals("image/jpeg", viewModel.getMimeType(File("test.jpg")))
        assertEquals("image/jpeg", viewModel.getMimeType(File("test.jpeg")))
        assertEquals("image/png", viewModel.getMimeType(File("test.png")))
        assertEquals("image/tiff", viewModel.getMimeType(File("test.tif")))
        assertNull(viewModel.getMimeType(File("test.exe")))
        assertNull(viewModel.getMimeType(File("test.txt")))
    }

    @Test
    fun `SCAN_STATUS_POLLING_STOPS_ON_READY_FOR_REVIEW_OR_ERROR`() = runTest {
        val statusDone = ScanStatusDto(documentId = 888L, ingestionStatus = 5) // ReadyForReview

        whenever(scanApi.getStatus(888L)).thenReturn(statusDone)

        val viewModel = ScanStatusViewModel(scanRepository, 888L)

        assertTrue(viewModel.uiState is ScanStatusUiState.Success)
        val state = viewModel.uiState as ScanStatusUiState.Success
        assertEquals(5, state.status.ingestionStatus)
        assertFalse(state.isTimeout)
    }
}

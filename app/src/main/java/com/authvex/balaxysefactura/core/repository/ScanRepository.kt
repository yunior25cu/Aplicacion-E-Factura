package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.*
import java.io.File

open class ScanRepository(
    private val scanApi: ScanApi,
    private val blobUploader: BlobUploader = BlobUploader()
) {

    open suspend fun uploadScannedDocument(
        file: File,
        contentType: String,
        target: ScanTarget
    ): Result<Long> {
        return try {
            // Validation: Max 25 MB
            val maxBytes = 25L * 1024 * 1024
            if (file.length() > maxBytes) {
                return Result.failure(Exception("El archivo excede el límite permitido de 25 MB"))
            }

            // 1. Init Upload (POST /api/v1/ocr/mobile/init)
            val initReq = ScanInitUploadRequest(
                fileName = file.name,
                contentType = contentType,
                fileSize = file.length(),
                target = target.apiValue
            )
            val initRes = scanApi.initUpload(initReq)

            // 2. Direct PUT to Azure Blob SAS (NO Bearer Token)
            val blobRes = blobUploader.uploadToAzureBlob(
                uploadUrl = initRes.uploadUrl,
                file = file,
                contentType = contentType
            )
            if (blobRes.isFailure) {
                return Result.failure(blobRes.exceptionOrNull() ?: Exception("No se pudo subir el documento"))
            }

            // 3. Complete Upload (POST /api/v1/ocr/mobile/complete)
            val completeReq = ScanCompleteUploadRequest(
                documentId = initRes.documentId,
                fileSize = file.length()
            )
            scanApi.completeUpload(completeReq)

            Result.success(initRes.documentId)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getStatus(documentId: Long): Result<ScanStatusDto> {
        return try {
            val status = scanApi.getStatus(documentId)
            Result.success(status)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getInbox(target: ScanTarget? = null): Result<List<ScanInboxItemDto>> {
        return try {
            val response = scanApi.getInbox(target?.apiValue)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }
}

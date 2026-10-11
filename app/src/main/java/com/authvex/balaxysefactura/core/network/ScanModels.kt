package com.authvex.balaxysefactura.core.network

import kotlinx.serialization.Serializable

enum class ScanTarget(val apiValue: Int) {
    PURCHASE(1),
    SALE(2)
}

@Serializable
data class ScanInitUploadRequest(
    val fileName: String,
    val contentType: String,
    val fileSize: Long,
    val target: Int = 2
)

@Serializable
data class ScanInitUploadResponse(
    val documentId: Long,
    val uploadUrl: String,
    val blobPath: String,
    val uploadExpiresAt: String
)

@Serializable
data class ScanCompleteUploadRequest(
    val documentId: Long,
    val fileSize: Long
)

@Serializable
data class ScanStatusDto(
    val documentId: Long,
    val batchId: Long? = null,
    val target: Int? = null,
    val ingestionStatus: Int? = null,
    val reviewStatus: Int? = null,
    val queueStatus: Int? = null,
    val sourceBlobPath: String? = null,
    val ingestionErrorMessage: String? = null,
    val documentNumber: String? = null,
    val partnerName: String? = null
)

@Serializable
data class ScanInboxItemDto(
    val documentId: Long = 0,
    val batchId: Long? = null,
    val target: Int? = null,
    val targetName: String? = null,
    val sourceOrigin: Int? = null,
    val sourceOriginName: String? = null,
    val ingestionStatus: Int? = null,
    val ingestionStatusName: String? = null,
    val reviewStatus: Int? = null,
    val reviewStatusName: String? = null,
    val partnerName: String? = null,
    val partnerTaxId: String? = null,
    val number: String? = null,
    val issueDate: String? = null,
    val total: Double? = null,
    val technicalError: String? = null,
    val createdAt: String? = null,
    val mobileUploadedAt: String? = null
)

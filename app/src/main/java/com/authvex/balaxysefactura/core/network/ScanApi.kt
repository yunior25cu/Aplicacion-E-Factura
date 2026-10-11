package com.authvex.balaxysefactura.core.network

import retrofit2.http.*

interface ScanApi {

    @POST("ocr/mobile/init")
    suspend fun initUpload(@Body request: ScanInitUploadRequest): ScanInitUploadResponse

    @POST("ocr/mobile/complete")
    suspend fun completeUpload(@Body request: ScanCompleteUploadRequest): ScanStatusDto

    @GET("ocr/mobile/status/{documentId}")
    suspend fun getStatus(@Path("documentId") documentId: Long): ScanStatusDto

    @GET("ocr/mobile/inbox")
    suspend fun getInbox(
        @Query("target") target: Int? = null
    ): List<ScanInboxItemDto>
}

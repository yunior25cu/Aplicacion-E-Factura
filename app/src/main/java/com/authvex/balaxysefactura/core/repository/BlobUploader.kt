package com.authvex.balaxysefactura.core.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

open class BlobUploader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build() // Clean unauthenticated OkHttpClient instance!
) {

    open suspend fun uploadToAzureBlob(
        uploadUrl: String,
        file: File,
        contentType: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val mediaType = contentType.toMediaTypeOrNull()
            val requestBody = file.asRequestBody(mediaType)

            val request = Request.Builder()
                .url(uploadUrl)
                .put(requestBody)
                .header("x-ms-blob-type", "BlockBlob")
                .header("Content-Type", contentType)
                .build() // NO Authorization Bearer header added!

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Error al subir el archivo al almacenamiento: ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.authvex.balaxysefactura.ui.screens.cfe.detail

import android.content.Context
import com.authvex.balaxysefactura.core.network.CfeDetailDto
import com.authvex.balaxysefactura.core.repository.CfeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream

object CfePdfDownloader {

    private fun sanitizeFilename(part: String): String {
        return part.replace(Regex("[/\\\\:*?\"<>|]"), "-")
    }

    suspend fun downloadAndSave(
        context: Context,
        cfeRepository: CfeRepository,
        documentId: Long,
        cfeDetail: CfeDetailDto? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val result = cfeRepository.downloadCfePdf(documentId)
        if (result.isFailure) {
            return@withContext Result.failure(result.exceptionOrNull()!!)
        }

        val body: ResponseBody = result.getOrNull()!!
        var outputStream: FileOutputStream? = null
        var tempFile: File? = null

        try {
            val dir = File(context.cacheDir, "cfe")
            if (!dir.exists()) dir.mkdirs()

            val fileName = if (cfeDetail?.cfeCode != null && !cfeDetail.serie.isNullOrBlank() && cfeDetail.numero != null) {
                "CFE_${cfeDetail.cfeCode}_${sanitizeFilename(cfeDetail.serie)}_${cfeDetail.numero}.pdf"
            } else {
                "CFE_$documentId.pdf"
            }

            val finalFile = File(dir, fileName)
            tempFile = File(dir, "temp_$fileName")

            outputStream = FileOutputStream(tempFile)
            body.byteStream().use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            if (finalFile.exists()) finalFile.delete()
            tempFile.renameTo(finalFile)

            Result.success(finalFile)
        } catch (e: Exception) {
            if (tempFile?.exists() == true) tempFile.delete()
            Result.failure(e)
        } finally {
            outputStream?.close()
        }
    }
}

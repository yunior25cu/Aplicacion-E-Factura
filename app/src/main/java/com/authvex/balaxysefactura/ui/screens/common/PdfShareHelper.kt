package com.authvex.balaxysefactura.ui.screens.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object PdfShareHelper {

    fun sharePdf(
        context: Context,
        pdfFile: File,
        subject: String = "Comprobante Electrónico",
        text: String = "Adjunto comprobante electrónico",
        chooserTitle: String = "Compartir comprobante electrónico"
    ): Result<Unit> {
        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, chooserTitle)
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

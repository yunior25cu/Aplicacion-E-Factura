package com.authvex.balaxysefactura.ui.screens.collection.detail

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.authvex.balaxysefactura.core.network.CobroDetailDto
import com.authvex.balaxysefactura.core.network.EmpresaDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class ReceiptAppliedInvoiceModel(
    val documentId: Long,
    val fiscalReference: String,
    val isFallback: Boolean,
    val fecha: String,
    val amount: Double
)

object CollectionReceiptPdfGenerator {

    fun sanitizeFilename(folio: String): String {
        return folio.replace(Regex("[/\\\\:*?\"<>|]"), "-")
    }

    suspend fun generate(
        context: Context,
        collection: CobroDetailDto,
        company: EmpresaDto? = null,
        appliedInvoices: List<ReceiptAppliedInvoiceModel> = emptyList()
    ): Result<File> = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        var currentFileOutputStream: FileOutputStream? = null
        var tempFile: File? = null

        try {
            val dir = File(context.cacheDir, "cobros")
            if (!dir.exists()) dir.mkdirs()

            val folioStr = collection.folio?.takeIf { it.isNotBlank() } ?: "CO-${collection.numero ?: collection.id}"
            val cleanFolio = sanitizeFilename(folioStr)
            val fileName = "Recibo_$cleanFolio.pdf"
            val finalFile = File(dir, fileName)
            tempFile = File(dir, "temp_$fileName")

            val pageWidth = 595
            val pageHeight = 842
            val margin = 36f
            val contentWidth = pageWidth - (margin * 2)

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 16f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val subtitlePaint = TextPaint().apply {
                color = Color.DKGRAY
                textSize = 11f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val bodyPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 10f
                isAntiAlias = true
            }

            val bodyBoldPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 10f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }

            var y = margin + 10f

            // Company Header
            val compName = company?.nombre?.takeIf { it.isNotBlank() } ?: "Balaxys ERP"
            canvas.drawText(compName, margin, y, titlePaint)
            y += 18f

            if (!company?.contacto?.direccion.isNullOrBlank()) {
                canvas.drawText(company?.contacto?.direccion ?: "", margin, y, bodyPaint)
                y += 14f
            }

            y += 8f
            canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
            y += 20f

            // Document Title
            canvas.drawText("RECIBO DE COBRO", margin, y, titlePaint)
            y += 20f

            // Document Metadata
            canvas.drawText("Cobro: $folioStr", margin, y, bodyBoldPaint)
            val dateStr = collection.fechaConfirmacion?.take(10) ?: collection.fechaEmision.take(10)
            canvas.drawText("Fecha: $dateStr", margin + 250f, y, bodyPaint)
            canvas.drawText("Estado: Confirmado", margin + 400f, y, bodyBoldPaint)
            y += 18f

            // Client Info
            val clientName = collection.cliente?.nombre ?: "Cliente no especificado"
            canvas.drawText("Cliente: $clientName", margin, y, bodyBoldPaint)
            if (!collection.cliente?.documentNumber.isNullOrBlank()) {
                canvas.drawText("Doc/RUT: ${collection.cliente?.documentNumber}", margin + 300f, y, bodyPaint)
            }
            y += 20f

            canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
            y += 18f

            // Financial Summary
            val symbol = collection.moneda?.codigo ?: "UYU"
            val totalAmount = if (collection.montoTotalOriginal > 0) collection.montoTotalOriginal else collection.montoTotalBase

            canvas.drawText("Datos Financieros", margin, y, subtitlePaint)
            y += 16f

            canvas.drawText("Moneda: ${collection.moneda?.nombre ?: symbol}", margin, y, bodyPaint)
            canvas.drawText("Importe Cobrado: $symbol ${String.format(Locale.US, "%.2f", totalAmount)}", margin + 250f, y, bodyBoldPaint)
            y += 14f

            collection.formaPago?.let { fp ->
                canvas.drawText("Forma de Pago: ${fp.nombre}", margin, y, bodyPaint)
            }
            collection.cuentaBanco?.let { cb ->
                canvas.drawText("Cuenta: ${cb.getDisplayLabel()}", margin + 250f, y, bodyPaint)
            }
            y += 14f

            if (!collection.numeroReferencia.isNullOrBlank()) {
                canvas.drawText("N° Referencia: ${collection.numeroReferencia}", margin, y, bodyPaint)
                y += 14f
            }
            if (!collection.nota.isNullOrBlank()) {
                canvas.drawText("Nota: ${collection.nota}", margin, y, bodyPaint)
                y += 14f
            }

            y += 10f
            canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
            y += 18f

            // Invoices Applied Table
            if (appliedInvoices.isNotEmpty()) {
                canvas.drawText("Facturas aplicadas", margin, y, subtitlePaint)
                y += 16f

                // Table Header
                canvas.drawText("CFE", margin, y, bodyBoldPaint)
                canvas.drawText("Fecha", margin + 220f, y, bodyBoldPaint)
                canvas.drawText("Monto aplicado", margin + 400f, y, bodyBoldPaint)
                y += 8f
                canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
                y += 14f

                appliedInvoices.forEach { item ->
                    canvas.drawText(item.fiscalReference, margin, y, bodyPaint)
                    canvas.drawText(item.fecha, margin + 220f, y, bodyPaint)
                    canvas.drawText("$symbol ${String.format(Locale.US, "%,.2f", item.amount)}", margin + 400f, y, bodyPaint)
                    y += 16f
                }

                y += 8f
                canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
                y += 20f
            }

            // Total Summary
            val grandTotalText = "Total Cobrado: $symbol ${String.format(Locale.US, "%,.2f", totalAmount)}"
            canvas.drawText(grandTotalText, margin, y, titlePaint)
            y += 30f

            // Document Footer Note
            val footerText = "Documento generado desde Balaxys"
            val footerPaint = TextPaint().apply {
                color = Color.GRAY
                textSize = 9f
                isAntiAlias = true
            }
            val footerX = (pageWidth - footerPaint.measureText(footerText)) / 2f
            canvas.drawText(footerText, footerX, pageHeight - margin - 10f, footerPaint)

            pdfDocument.finishPage(page)

            tempFile = File(dir, "temp_$fileName")
            currentFileOutputStream = FileOutputStream(tempFile)
            pdfDocument.writeTo(currentFileOutputStream)

            if (finalFile.exists()) finalFile.delete()
            tempFile.renameTo(finalFile)

            Result.success(finalFile)
        } catch (e: Exception) {
            if (tempFile?.exists() == true) tempFile.delete()
            Result.failure(e)
        } finally {
            try { currentFileOutputStream?.close() } catch (_: Exception) {}
            try { pdfDocument.close() } catch (_: Exception) {}
        }
    }
}

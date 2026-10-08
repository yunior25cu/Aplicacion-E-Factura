package com.authvex.balaxysefactura.ui.screens.budget.detail

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import com.authvex.balaxysefactura.core.network.BudgetDto
import com.authvex.balaxysefactura.core.network.EmpresaDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object BudgetPdfGenerator {

    private fun sanitizeFilename(folio: String): String {
        return folio.replace(Regex("[/\\\\:*?\"<>|]"), "-")
    }

    private fun decodeCompanyLogo(logoStr: String?): Bitmap? {
        if (logoStr.isNullOrBlank()) return null
        return try {
            val cleanStr = if (logoStr.contains(",")) logoStr.substringAfter(",") else logoStr
            val decodedBytes = Base64.decode(cleanStr, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (_: Exception) {
            try {
                if (logoStr.startsWith("http://") || logoStr.startsWith("https://")) {
                    val url = URL(logoStr)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.doInput = true
                    conn.connect()
                    val inputStream = conn.inputStream
                    BitmapFactory.decodeStream(inputStream)
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun generate(
        context: Context,
        budget: BudgetDto,
        company: EmpresaDto
    ): Result<File> = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        var currentFileOutputStream: FileOutputStream? = null
        var tempFile: File? = null

        try {
            val model = BudgetPdfPresentationBuilder.build(budget, company)
            val logoBitmap = decodeCompanyLogo(model.companyLogo)

            val dir = File(context.cacheDir, "presupuestos")
            if (!dir.exists()) dir.mkdirs()

            val fileName = "Presupuesto_${sanitizeFilename(model.folioText)}.pdf"
            val finalFile = File(dir, fileName)
            tempFile = File(dir, "temp_$fileName")

            // Page Constants (A4: 595 x 842 pt)
            val pageWidth = 595
            val pageHeight = 842
            val margin = 36f
            val contentWidth = pageWidth - (margin * 2)
            val pageBottomMargin = pageHeight - margin

            // Paint Setup
            val textPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 10f
                isAntiAlias = true
            }

            val boldPaint = TextPaint(textPaint).apply {
                isFakeBoldText = true
            }

            val titlePaint = TextPaint(textPaint).apply {
                textSize = 16f
                isFakeBoldText = true
            }

            val subtitlePaint = TextPaint(textPaint).apply {
                textSize = 12f
                isFakeBoldText = true
            }

            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            fun drawHeader(canvas: Canvas, isFirstPage: Boolean): Float {
                var y = margin + 10f

                if (isFirstPage) {
                    // Draw Logo or Company Name
                    if (logoBitmap != null) {
                        val logoWidth = 70f
                        val logoHeight = (logoBitmap.height.toFloat() / logoBitmap.width.toFloat()) * logoWidth
                        val rect = RectF(margin, y, margin + logoWidth, y + logoHeight)
                        canvas.drawBitmap(logoBitmap, null, rect, null)
                    }

                    // Company Details (Right-aligned)
                    val companyX = margin + contentWidth
                    canvas.drawText(model.companyName, companyX - subtitlePaint.measureText(model.companyName), y + 12f, subtitlePaint)
                    var companyY = y + 26f
                    if (model.companyAddress.isNotBlank()) {
                        val addr = "Dirección: ${model.companyAddress}"
                        canvas.drawText(addr, companyX - textPaint.measureText(addr), companyY, textPaint)
                        companyY += 14f
                    }
                    if (model.companyPhone.isNotBlank()) {
                        val phone = "Teléfono: ${model.companyPhone}"
                        canvas.drawText(phone, companyX - textPaint.measureText(phone), companyY, textPaint)
                        companyY += 14f
                    }
                    if (model.companyEmail.isNotBlank()) {
                        val email = "Email: ${model.companyEmail}"
                        canvas.drawText(email, companyX - textPaint.measureText(email), companyY, textPaint)
                        companyY += 14f
                    }

                    y = Math.max(y + 60f, companyY + 10f)
                    canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
                    y += 18f

                    // Document Title & Metadata
                    canvas.drawText(model.titleText, margin, y, titlePaint)
                    canvas.drawText("Almacén: ${model.almacenText}", companyX - textPaint.measureText("Almacén: ${model.almacenText}"), y, textPaint)
                    y += 18f

                    canvas.drawText("Estado: ${model.estadoText}", margin, y, boldPaint)
                    canvas.drawText("Emisión: ${model.fechaEmisionText}", companyX - textPaint.measureText("Emisión: ${model.fechaEmisionText}"), y, textPaint)
                    y += 16f

                    if (!model.fechaConfirmacionText.isNullOrBlank()) {
                        canvas.drawText("Confirmación: ${model.fechaConfirmacionText}", companyX - textPaint.measureText("Confirmación: ${model.fechaConfirmacionText}"), y, textPaint)
                        y += 16f
                    }

                    canvas.drawText("Entrega: ${model.fechaVencimientoText}", companyX - textPaint.measureText("Entrega: ${model.fechaVencimientoText}"), y, textPaint)
                    y += 16f

                    canvas.drawText("Cliente: ${model.clienteText}", margin, y, textPaint)
                    y += 14f
                    canvas.drawText("Moneda: ${model.monedaText}", margin, y, textPaint)
                    y += 20f
                } else {
                    // Compact Header for Page 2+
                    canvas.drawText("${model.titleText} (Cont.)", margin, y + 10f, subtitlePaint)
                    canvas.drawText("Página $pageNumber", margin + contentWidth - textPaint.measureText("Página $pageNumber"), y + 10f, textPaint)
                    y += 24f
                    canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
                    y += 14f
                }

                return y
            }

            fun drawTableHeader(canvas: Canvas, yPos: Float, showDiscount: Boolean): Float {
                var y = yPos
                canvas.drawText("Código", margin, y, boldPaint)
                canvas.drawText("Descripción", margin + 65f, y, boldPaint)
                canvas.drawText("UM", margin + 220f, y, boldPaint)
                canvas.drawText("Cant.", margin + 255f, y, boldPaint)
                canvas.drawText("Precio", margin + 300f, y, boldPaint)

                var currentX = margin + 360f
                if (showDiscount) {
                    canvas.drawText("Dto.", currentX, y, boldPaint)
                    currentX += 40f
                }
                canvas.drawText("IVA", currentX, y, boldPaint)
                canvas.drawText("Importe", margin + contentWidth - boldPaint.measureText("Importe"), y, boldPaint)

                y += 8f
                canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
                y += 14f
                return y
            }

            var currentY = drawHeader(canvas, isFirstPage = true)
            currentY = drawTableHeader(canvas, currentY, model.showDiscountColumn)

            // Draw Table Rows
            model.lines.forEach { line ->
                // Measure Description Text Wrapping
                val descWidth = 150
                val descLayout = StaticLayout.Builder.obtain(line.descripcion, 0, line.descripcion.length, textPaint, descWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .build()

                val rowHeight = Math.max(20f, descLayout.height.toFloat() + 6f)

                // Page Break Check
                if (currentY + rowHeight > pageBottomMargin) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = drawHeader(canvas, isFirstPage = false)
                    currentY = drawTableHeader(canvas, currentY, model.showDiscountColumn)
                }

                // Draw Row Columns
                canvas.drawText(line.codigo, margin, currentY + 10f, textPaint)

                canvas.save()
                canvas.translate(margin + 65f, currentY)
                descLayout.draw(canvas)
                canvas.restore()

                canvas.drawText(line.um, margin + 220f, currentY + 10f, textPaint)
                canvas.drawText(line.cantidadText, margin + 255f, currentY + 10f, textPaint)
                canvas.drawText(line.precioText, margin + 300f, currentY + 10f, textPaint)

                var currentX = margin + 360f
                if (model.showDiscountColumn) {
                    canvas.drawText(line.descuentoPercentText ?: "-", currentX, currentY + 10f, textPaint)
                    currentX += 40f
                }
                canvas.drawText(line.ivaText, currentX, currentY + 10f, textPaint)
                canvas.drawText(line.importeText, margin + contentWidth - textPaint.measureText(line.importeText), currentY + 10f, textPaint)

                currentY += rowHeight
                canvas.drawLine(margin, currentY, margin + contentWidth, currentY, linePaint)
                currentY += 6f
            }

            // Calculate Totals Block Height
            val totalsHeight = 120f + (model.ivaBreakdown.size * 14f)

            if (currentY + totalsHeight > pageBottomMargin) {
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                currentY = drawHeader(canvas, isFirstPage = false)
            }

            // Draw Totals Block (Right Aligned)
            currentY += 10f
            val totalsX = margin + contentWidth

            if (model.subtotalBrutoText != null) {
                val subBruto = "Subtotal Bruto: ${model.subtotalBrutoText}"
                canvas.drawText(subBruto, totalsX - textPaint.measureText(subBruto), currentY, textPaint)
                currentY += 14f
            }
            if (model.descuentoLineasText != null) {
                val dtoLineas = "Descuento Líneas: ${model.descuentoLineasText}"
                canvas.drawText(dtoLineas, totalsX - textPaint.measureText(dtoLineas), currentY, textPaint)
                currentY += 14f
            }
            if (model.descuentoGlobalText != null) {
                val dtoGlobal = "Descuento Global: ${model.descuentoGlobalText}"
                canvas.drawText(dtoGlobal, totalsX - textPaint.measureText(dtoGlobal), currentY, textPaint)
                currentY += 14f
            }

            val subNeto = "Subtotal: ${model.subtotalNetoText}"
            canvas.drawText(subNeto, totalsX - textPaint.measureText(subNeto), currentY, textPaint)
            currentY += 14f

            val ivaTotal = "IVA Total: ${model.ivaTotalText}"
            canvas.drawText(ivaTotal, totalsX - textPaint.measureText(ivaTotal), currentY, textPaint)
            currentY += 14f

            model.ivaBreakdown.forEach { breakdown ->
                val ivaLine = "${breakdown.label}: ${breakdown.amountText}"
                canvas.drawText(ivaLine, totalsX - textPaint.measureText(ivaLine), currentY, textPaint)
                currentY += 14f
            }

            if (model.ajusteRedondeoText != null) {
                val redondeo = "Ajuste Redondeo: ${model.ajusteRedondeoText}"
                canvas.drawText(redondeo, totalsX - textPaint.measureText(redondeo), currentY, textPaint)
                currentY += 14f
            }

            canvas.drawLine(totalsX - 180f, currentY, totalsX, currentY, linePaint)
            currentY += 14f

            val totalLine = "Importe Total: ${model.importeTotalText}"
            canvas.drawText(totalLine, totalsX - subtitlePaint.measureText(totalLine), currentY, subtitlePaint)
            currentY += 24f

            // Draw Terms and Notes
            if (model.terminoCondicionesText != null || model.notaText != null) {
                if (currentY + 60f > pageBottomMargin) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = drawHeader(canvas, isFirstPage = false)
                }

                if (model.terminoCondicionesText != null) {
                    canvas.drawText("Términos y Condiciones:", margin, currentY, boldPaint)
                    currentY += 14f
                    canvas.drawText(model.terminoCondicionesText, margin, currentY, textPaint)
                    currentY += 20f
                }

                if (model.notaText != null) {
                    canvas.drawText("Nota:", margin, currentY, boldPaint)
                    currentY += 14f
                    canvas.drawText(model.notaText, margin, currentY, textPaint)
                    currentY += 20f
                }
            }

            document.finishPage(page)

            // Atomic file write using temporary file
            currentFileOutputStream = FileOutputStream(tempFile)
            document.writeTo(currentFileOutputStream)
            currentFileOutputStream.flush()
            currentFileOutputStream.close()
            currentFileOutputStream = null

            if (finalFile.exists()) finalFile.delete()
            tempFile.renameTo(finalFile)

            Result.success(finalFile)
        } catch (e: Exception) {
            if (tempFile?.exists() == true) tempFile.delete()
            Result.failure(e)
        } finally {
            currentFileOutputStream?.close()
            document.close()
        }
    }
}

package com.authvex.balaxysefactura.ui.screens.reports.collections.aging

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class AgingReportDocumentModel(
    val companyName: String,
    val fechaDesde: String,
    val fechaHasta: String,
    val baseCurrencyCode: String,
    val clientFilterLabel: String,
    val state: AgingReportUiState.Success
)

object AgingReportPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 36f
    private const val MARGIN_BOTTOM = 50f

    fun generate(
        context: Context,
        model: AgingReportDocumentModel
    ): Result<File> {
        val pdfDoc = PdfDocument()
        return try {
            val dir = File(context.cacheDir, "reportes/cobros")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val sanitizedDesde = sanitize(model.fechaDesde)
            val sanitizedHasta = sanitize(model.fechaHasta)
            val fileName = "Antiguedad_Deuda_${sanitizedDesde}_${sanitizedHasta}.pdf"
            val tempFile = File(dir, "temp_${System.currentTimeMillis()}.pdf")
            val targetFile = File(dir, fileName)

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = pdfDoc.startPage(pageInfo)
            var canvas = page.canvas

            val titlePaint = Paint().apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 18f
                color = Color.parseColor("#F57C00")
            }

            val subTitlePaint = Paint().apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = 10f
                color = Color.parseColor("#616161")
            }

            val sectionTitlePaint = Paint().apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 12f
                color = Color.parseColor("#F57C00")
            }

            val textBold = Paint().apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 10f
                color = Color.parseColor("#212121")
            }

            val textNormal = Paint().apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = 10f
                color = Color.parseColor("#424242")
            }

            val linePaint = Paint().apply {
                color = Color.parseColor("#E0E0E0")
                strokeWidth = 1f
            }

            var currentY = 40f

            fun checkPageSpace(requiredHeight: Float) {
                if (currentY + requiredHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    pdfDoc.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = pdfDoc.startPage(pageInfo)
                    canvas = page.canvas

                    canvas.drawText("Balaxys e-Factura — Antigüedad de Deuda (${model.fechaDesde} - ${model.fechaHasta})", MARGIN_X, 30f, subTitlePaint)
                    canvas.drawLine(MARGIN_X, 36f, PAGE_WIDTH - MARGIN_X, 36f, linePaint)
                    currentY = 50f
                }
            }

            // --- HEADER ---
            canvas.drawText("Balaxys e-Factura", MARGIN_X, currentY, subTitlePaint)
            currentY += 22f
            canvas.drawText("ANTIGÜEDAD DE DEUDA (AGING)", MARGIN_X, currentY, titlePaint)
            currentY += 16f
            canvas.drawText("Empresa: ${model.companyName}", MARGIN_X, currentY, textBold)
            currentY += 14f

            val formattedDesde = formatDateDisplay(model.fechaDesde)
            val formattedHasta = formatDateDisplay(model.fechaHasta)
            canvas.drawText("Período: $formattedDesde — $formattedHasta", MARGIN_X, currentY, subTitlePaint)
            currentY += 14f
            canvas.drawText("Cliente: ${model.clientFilterLabel} | Importes expresados en: ${model.baseCurrencyCode}", MARGIN_X, currentY, subTitlePaint)
            currentY += 10f
            canvas.drawLine(MARGIN_X, currentY, PAGE_WIDTH - MARGIN_X, currentY, linePaint)
            currentY += 18f

            if (model.state.hasFallback) {
                canvas.drawText("Aviso: Algunos documentos no tenían un vencimiento explícito. Se utilizó fecha de respaldo.", MARGIN_X, currentY, subTitlePaint)
                currentY += 16f
            }

            // --- RESUMEN DE ANTIGÜEDAD POR TRAMOS ---
            checkPageSpace(120f)
            canvas.drawText("RESUMEN DE ANTIGÜEDAD POR TRAMOS", MARGIN_X, currentY, sectionTitlePaint)
            currentY += 16f

            val kpiWidth = (PAGE_WIDTH - 2 * MARGIN_X - 20) / 3f

            drawKpiCard(canvas, MARGIN_X, currentY, kpiWidth, 48f, "No vencido", "${model.baseCurrencyCode} ${formatMoney(model.state.sumNoVencido)}")
            drawKpiCard(canvas, MARGIN_X + kpiWidth + 10, currentY, kpiWidth, 48f, "1–30 días", "${model.baseCurrencyCode} ${formatMoney(model.state.sum1a30)}")
            drawKpiCard(canvas, MARGIN_X + (kpiWidth + 10) * 2, currentY, kpiWidth, 48f, "31–60 días", "${model.baseCurrencyCode} ${formatMoney(model.state.sum31a60)}")

            currentY += 56f

            drawKpiCard(canvas, MARGIN_X, currentY, kpiWidth, 48f, "61–90 días", "${model.baseCurrencyCode} ${formatMoney(model.state.sum61a90)}")
            drawKpiCard(canvas, MARGIN_X + kpiWidth + 10, currentY, kpiWidth, 48f, "+91 días", "${model.baseCurrencyCode} ${formatMoney(model.state.sum91Mas)}")
            drawKpiCard(canvas, MARGIN_X + (kpiWidth + 10) * 2, currentY, kpiWidth, 48f, "Total Vencido", "${model.baseCurrencyCode} ${formatMoney(model.state.totalVencido)}")

            currentY += 64f

            // --- TOP CLIENTES CON DEUDA VENCIDA ---
            if (model.state.clientRanking.isNotEmpty()) {
                checkPageSpace(40f + (model.state.clientRanking.size * 18f))
                canvas.drawText("CLIENTES CON MAYOR DEUDA VENCIDA", MARGIN_X, currentY, sectionTitlePaint)
                currentY += 16f

                model.state.clientRanking.forEachIndexed { idx, item ->
                    checkPageSpace(18f)
                    val label = "${idx + 1}. ${item.clientName} (${item.mainBucketLabel})"
                    canvas.drawText(label.take(45), MARGIN_X + 10, currentY, textNormal)
                    val valStr = "${model.baseCurrencyCode} ${formatMoney(item.totalVencido)}"
                    canvas.drawText(valStr, PAGE_WIDTH - MARGIN_X - textBold.measureText(valStr), currentY, textBold)
                    currentY += 16f
                }
                currentY += 10f
            }

            // --- FOOTER ---
            checkPageSpace(45f)
            canvas.drawLine(MARGIN_X, currentY, PAGE_WIDTH - MARGIN_X, currentY, linePaint)
            currentY += 14f

            canvas.drawText("Documentos confirmados. No incluye documentos anulados ni sin confirmar.", MARGIN_X, currentY, subTitlePaint)
            currentY += 12f
            val genDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date())
            canvas.drawText("Generado desde Balaxys e-Factura el $genDate. - Página $pageNumber", MARGIN_X, currentY, subTitlePaint)

            pdfDoc.finishPage(page)

            val outputStream = FileOutputStream(tempFile)
            pdfDoc.writeTo(outputStream)
            outputStream.close()

            if (targetFile.exists()) {
                targetFile.delete()
            }
            tempFile.renameTo(targetFile)

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            pdfDoc.close()
        }
    }

    private fun drawKpiCard(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, title: String, value: String) {
        val bgPaint = Paint().apply {
            color = Color.parseColor("#F5F5F5")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val labelPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 9f
            color = Color.parseColor("#616161")
        }
        val valPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f
            color = Color.parseColor("#F57C00")
        }

        canvas.drawRect(x, y, x + width, y + height, bgPaint)
        canvas.drawRect(x, y, x + width, y + height, borderPaint)

        canvas.drawText(title, x + 8, y + 16, labelPaint)
        canvas.drawText(value, x + 8, y + 34, valPaint)
    }

    private fun formatDateDisplay(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        val clean = dateStr.take(10)
        val parts = clean.split("-")
        return if (parts.size == 3) {
            "${parts[2]}/${parts[1]}/${parts[0]}"
        } else {
            clean
        }
    }

    private fun formatMoney(amount: Double): String {
        return String.format(Locale.US, "%,.2f", amount)
    }

    private fun sanitize(input: String): String {
        return input.replace("/", "-").replace("\\", "-").replace(":", "-")
    }
}

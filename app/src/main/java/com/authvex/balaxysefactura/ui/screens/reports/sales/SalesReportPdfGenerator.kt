package com.authvex.balaxysefactura.ui.screens.reports.sales

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.authvex.balaxysefactura.core.network.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class SalesReportDocumentModel(
    val companyName: String,
    val fechaDesde: String,
    val fechaHasta: String,
    val currencyCode: String,
    val currencySymbol: String,
    val resumen: VentasResumenResponse,
    val serie: VentasSerieResponse?,
    val porCliente: VentasPorClienteResponse?,
    val porProducto: VentasPorProductoResponse?,
    val porDocumento: VentasPorDocumentoResponse?
)

object SalesReportPdfGenerator {

    private const val PAGE_WIDTH = 595 // A4 width at 72 DPI
    private const val PAGE_HEIGHT = 842 // A4 height at 72 DPI
    private const val MARGIN_X = 36f
    private const val MARGIN_BOTTOM = 50f

    fun generate(
        context: Context,
        model: SalesReportDocumentModel
    ): Result<File> {
        val pdfDoc = PdfDocument()
        return try {
            val dir = File(context.cacheDir, "reportes/ventas")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val sanitizedDesde = sanitize(model.fechaDesde)
            val sanitizedHasta = sanitize(model.fechaHasta)
            val fileName = "Informe_Ventas_${sanitizedDesde}_${sanitizedHasta}.pdf"
            val tempFile = File(dir, "temp_${System.currentTimeMillis()}.pdf")
            val targetFile = File(dir, fileName)

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = pdfDoc.startPage(pageInfo)
            var canvas = page.canvas

            val titlePaint = Paint().apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 18f
                color = Color.parseColor("#0288D1")
            }

            val subTitlePaint = Paint().apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textSize = 10f
                color = Color.parseColor("#616161")
            }

            val sectionTitlePaint = Paint().apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 12f
                color = Color.parseColor("#0288D1")
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

                    // Mini Header on new page
                    canvas.drawText("Balaxys e-Factura — Informe de Ventas (${model.fechaDesde} - ${model.fechaHasta})", MARGIN_X, 30f, subTitlePaint)
                    canvas.drawLine(MARGIN_X, 36f, PAGE_WIDTH - MARGIN_X, 36f, linePaint)
                    currentY = 50f
                }
            }

            // --- HEADER ---
            canvas.drawText("Balaxys e-Factura", MARGIN_X, currentY, subTitlePaint)
            currentY += 22f
            canvas.drawText("INFORME DE VENTAS", MARGIN_X, currentY, titlePaint)
            currentY += 16f
            canvas.drawText("Empresa: ${model.companyName}", MARGIN_X, currentY, textBold)
            currentY += 14f

            val formattedDesde = formatDateDisplay(model.fechaDesde)
            val formattedHasta = formatDateDisplay(model.fechaHasta)
            canvas.drawText("Período: $formattedDesde — $formattedHasta", MARGIN_X, currentY, subTitlePaint)
            currentY += 14f
            canvas.drawText("Importes expresados en: ${model.currencyCode} (${model.currencySymbol})", MARGIN_X, currentY, subTitlePaint)
            currentY += 10f
            canvas.drawLine(MARGIN_X, currentY, PAGE_WIDTH - MARGIN_X, currentY, linePaint)
            currentY += 18f

            // --- RESUMEN EJECUTIVO (KPIs) ---
            checkPageSpace(110f)
            canvas.drawText("RESUMEN EJECUTIVO", MARGIN_X, currentY, sectionTitlePaint)
            currentY += 16f

            val totalBase = model.resumen.totales.importeTotalBase
            val cantDocs = model.resumen.totales.cantidadDocumentos
            val ticketAvg = model.resumen.totales.ticketPromedioBase

            val kpiBoxWidth = (PAGE_WIDTH - 2 * MARGIN_X - 20) / 3f

            drawKpiCard(canvas, MARGIN_X, currentY, kpiBoxWidth, 54f, "Ventas Netas", "${model.currencyCode} ${formatMoney(totalBase)}")
            drawKpiCard(canvas, MARGIN_X + kpiBoxWidth + 10, currentY, kpiBoxWidth, 54f, "Documentos", "$cantDocs")
            drawKpiCard(canvas, MARGIN_X + (kpiBoxWidth + 10) * 2, currentY, kpiBoxWidth, 54f, "Ticket Promedio", "${model.currencyCode} ${formatMoney(ticketAvg)}")

            currentY += 68f

            // Comparación período anterior si existe
            model.resumen.comparacion?.let { comp ->
                checkPageSpace(30f)
                val varPct = comp.variacionImportePct
                val varText = if (varPct != null) String.format(Locale.US, "%.1f%%", varPct) else "N/A"
                canvas.drawText("Variación vs período anterior (${formatDateDisplay(comp.fechaDesdeAnterior)} - ${formatDateDisplay(comp.fechaHastaAnterior)}): $varText", MARGIN_X, currentY, subTitlePaint)
                currentY += 20f
            }

            // --- DESGLOSE POR TIPO DE DOCUMENTO ---
            model.porDocumento?.items?.let { docItems ->
                if (docItems.isNotEmpty()) {
                    checkPageSpace(40f + (docItems.size * 18f))
                    canvas.drawText("DETALLE POR TIPO DE DOCUMENTO", MARGIN_X, currentY, sectionTitlePaint)
                    currentY += 16f

                    docItems.forEach { item ->
                        checkPageSpace(18f)
                        canvas.drawText(item.etiqueta, MARGIN_X + 10, currentY, textNormal)
                        val valStr = "${model.currencyCode} ${formatMoney(item.importeTotalBase)}"
                        canvas.drawText(valStr, PAGE_WIDTH - MARGIN_X - textBold.measureText(valStr), currentY, textBold)
                        currentY += 16f
                    }
                    currentY += 10f
                }
            }

            // --- EVOLUCIÓN DE VENTAS (SERIE) ---
            model.serie?.puntos?.let { puntos ->
                if (puntos.isNotEmpty()) {
                    checkPageSpace(40f + (puntos.take(15).size * 16f))
                    canvas.drawText("EVOLUCIÓN TEMPORAL", MARGIN_X, currentY, sectionTitlePaint)
                    currentY += 16f

                    puntos.take(15).forEach { pt ->
                        checkPageSpace(16f)
                        canvas.drawText(pt.etiqueta, MARGIN_X + 10, currentY, textNormal)
                        val valStr = "${model.currencyCode} ${formatMoney(pt.importeTotalBase)}"
                        canvas.drawText(valStr, PAGE_WIDTH - MARGIN_X - textNormal.measureText(valStr), currentY, textNormal)
                        currentY += 15f
                    }
                    currentY += 10f
                }
            }

            // --- TOP CLIENTES ---
            model.porCliente?.items?.let { clients ->
                if (clients.isNotEmpty()) {
                    checkPageSpace(40f + (clients.take(10).size * 18f))
                    canvas.drawText("TOP CLIENTES", MARGIN_X, currentY, sectionTitlePaint)
                    currentY += 16f

                    clients.take(10).forEachIndexed { idx, client ->
                        checkPageSpace(18f)
                        val label = "${idx + 1}. ${client.denominacion}"
                        canvas.drawText(label.take(45), MARGIN_X + 10, currentY, textNormal)
                        val valStr = "${model.currencyCode} ${formatMoney(client.importeTotalBase)}"
                        canvas.drawText(valStr, PAGE_WIDTH - MARGIN_X - textBold.measureText(valStr), currentY, textBold)
                        currentY += 16f
                    }
                    currentY += 10f
                }
            }

            // --- TOP PRODUCTOS ---
            model.porProducto?.items?.let { products ->
                if (products.isNotEmpty()) {
                    checkPageSpace(40f + (products.take(10).size * 18f))
                    canvas.drawText("TOP PRODUCTOS Y SERVICIOS", MARGIN_X, currentY, sectionTitlePaint)
                    currentY += 16f

                    products.take(10).forEachIndexed { idx, prod ->
                        checkPageSpace(18f)
                        val unitStr = prod.unidadMedidaCodigo ?: "UN"
                        val qtyStr = String.format(Locale.US, "%.0f %s", prod.cantidad, unitStr)
                        val label = "${idx + 1}. ${prod.denominacion} ($qtyStr)"
                        canvas.drawText(label.take(45), MARGIN_X + 10, currentY, textNormal)
                        val valStr = "${model.currencyCode} ${formatMoney(prod.importeTotalBase)}"
                        canvas.drawText(valStr, PAGE_WIDTH - MARGIN_X - textBold.measureText(valStr), currentY, textBold)
                        currentY += 16f
                    }
                    currentY += 10f
                }
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
            textSize = 11f
            color = Color.parseColor("#0288D1")
        }

        canvas.drawRect(x, y, x + width, y + height, bgPaint)
        canvas.drawRect(x, y, x + width, y + height, borderPaint)

        canvas.drawText(title, x + 8, y + 18, labelPaint)
        canvas.drawText(value, x + 8, y + 38, valPaint)
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

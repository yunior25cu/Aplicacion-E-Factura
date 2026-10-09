package com.authvex.balaxysefactura.ui.screens.budget.detail

import com.authvex.balaxysefactura.core.network.BudgetDto
import com.authvex.balaxysefactura.core.network.BudgetEstado
import com.authvex.balaxysefactura.core.network.EmpresaDto
import java.util.Locale

data class BudgetPdfPresentationModel(
    val titleText: String,
    val isBaseCurrency: Boolean,
    val currencySymbol: String,
    val companyName: String,
    val companyAddress: String,
    val companyPhone: String,
    val companyEmail: String,
    val companyLogo: String?,
    val folioText: String,
    val estadoText: String,
    val almacenText: String,
    val fechaEmisionText: String,
    val fechaConfirmacionText: String?,
    val fechaVencimientoText: String,
    val clienteText: String,
    val monedaText: String,
    val showDiscountColumn: Boolean,
    val lines: List<BudgetPdfLinePresentation>,
    val subtotalBrutoText: String?,
    val descuentoLineasText: String?,
    val descuentoGlobalText: String?,
    val subtotalNetoText: String,
    val ivaTotalText: String,
    val ivaBreakdown: List<BudgetPdfIvaBreakdownPresentation>,
    val ajusteRedondeoText: String?,
    val importeTotalText: String,
    val terminoCondicionesText: String?,
    val notaText: String?
)

data class BudgetPdfLinePresentation(
    val codigo: String,
    val descripcion: String,
    val um: String,
    val cantidadText: String,
    val precioText: String,
    val descuentoPercentText: String?,
    val ivaText: String,
    val importeText: String
)

data class BudgetPdfIvaBreakdownPresentation(
    val label: String,
    val amountText: String
)

object BudgetPdfPresentationBuilder {

    fun formatMoney(amount: Double): String {
        return String.format(Locale.US, "%.2f", amount)
    }

    fun formatQuantity(quantity: Double): String {
        return if (quantity % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", quantity)
        } else {
            String.format(Locale.US, "%.4f", quantity).trimEnd('0').trimEnd('.')
        }
    }

    fun build(budget: BudgetDto, company: EmpresaDto): BudgetPdfPresentationModel {
        val isBaseCurrency = company.moneda == null || budget.moneda?.id == company.moneda.id
        val symbol = budget.moneda?.codigo ?: "UYU"

        val folioText = budget.folio?.takeIf { it.isNotBlank() } ?: "N° ${budget.numero ?: budget.id}"
        val estadoEnum = BudgetEstado.fromCode(budget.estado)
        val estadoText = when {
            budget.factura != null -> "Facturado"
            else -> estadoEnum.label
        }

        // Lines & Discount Detection
        val linePresentations = mutableListOf<BudgetPdfLinePresentation>()
        var hasAnyLineDiscount = false

        budget.documentoProductos.forEach { item ->
            val unitPrice = if (isBaseCurrency) item.precioBase else item.precioOriginal
            val lineIva = if (isBaseCurrency) (item.iva ?: 0.0) else (item.ivaOriginal ?: 0.0)
            val lineImporte = if (isBaseCurrency) (item.importeBase ?: 0.0) else (item.importeOriginal ?: 0.0)

            val lineDiscount = if (isBaseCurrency) item.descuento else (item.descuentoOriginal ?: item.descuento)
            val discountPercentText = if (lineDiscount > 0 && unitPrice > 0) {
                hasAnyLineDiscount = true
                val pct = (lineDiscount / (item.cantidad * unitPrice)) * 100.0
                String.format(Locale.US, "%.1f%%", pct)
            } else null

            linePresentations.add(
                BudgetPdfLinePresentation(
                    codigo = item.codigo ?: item.producto?.codigo ?: "",
                    descripcion = item.descripcion ?: item.producto?.nombre ?: "Producto",
                    um = item.um ?: "UN",
                    cantidadText = formatQuantity(item.cantidad),
                    precioText = formatMoney(unitPrice),
                    descuentoPercentText = discountPercentText,
                    ivaText = formatMoney(lineIva),
                    importeText = formatMoney(lineImporte)
                )
            )
        }

        // Totals from PERSISTED document values (no recalculation)
        val subtotalNetoValue = if (isBaseCurrency) (budget.importeBase ?: 0.0) else (budget.importeOriginal ?: 0.0)
        val ivaTotalValue = if (isBaseCurrency) (budget.iva ?: 0.0) else (budget.ivaOriginal ?: 0.0)
        val totalValue = if (isBaseCurrency) (budget.importeTotalBase ?: 0.0) else (budget.importeTotalOriginal ?: 0.0)
        val ajusteRedondeoValue = if (isBaseCurrency) budget.ajusteRedondeoBase else budget.ajusteRedondeoOriginal

        // IVA Breakdown by nominal tax rate label
        val ivaMap = mutableMapOf<String, Double>()
        budget.documentoProductos.forEach { item ->
            val taxRate = item.porcentajeIva ?: item.producto?.effectiveTaxRate ?: 0.0
            val pctLabel = if (taxRate > 0) String.format(Locale.US, "%.0f%%", taxRate * 100) else "0%"
            val label = "IVA $pctLabel"
            val itemIva = if (isBaseCurrency) (item.iva ?: 0.0) else (item.ivaOriginal ?: 0.0)
            ivaMap[label] = (ivaMap[label] ?: 0.0) + itemIva
        }

        val ivaBreakdownPresentations = ivaMap.map { (lbl, sumIva) ->
            BudgetPdfIvaBreakdownPresentation(
                label = lbl,
                amountText = "$symbol ${formatMoney(sumIva)}"
            )
        }

        val subtotalBrutoText = if (hasAnyLineDiscount || budget.descuento > 0) {
            val totalDiscounts = budget.descuento + budget.documentoProductos.sumOf { if (isBaseCurrency) it.descuento else (it.descuentoOriginal ?: it.descuento) }
            "$symbol ${formatMoney(subtotalNetoValue + totalDiscounts)}"
        } else null

        val descuentoLineasText = if (hasAnyLineDiscount) {
            val lineDiscountSum = budget.documentoProductos.sumOf { if (isBaseCurrency) it.descuento else (it.descuentoOriginal ?: it.descuento) }
            "$symbol ${formatMoney(lineDiscountSum)}"
        } else null

        val descuentoGlobalText = if (budget.descuento > 0) {
            "$symbol ${formatMoney(budget.descuento)}"
        } else null

        val ajusteRedondeoText = if (ajusteRedondeoValue != 0.0) {
            "$symbol ${formatMoney(ajusteRedondeoValue)}"
        } else null

        return BudgetPdfPresentationModel(
            titleText = "Presupuesto $folioText",
            isBaseCurrency = isBaseCurrency,
            currencySymbol = symbol,
            companyName = company.nombre.takeIf { it.isNotBlank() } ?: "Empresa",
            companyAddress = company.contacto?.direccion ?: "",
            companyPhone = company.contacto?.telefono ?: "",
            companyEmail = company.contacto?.email ?: "",
            companyLogo = company.logo,
            folioText = folioText,
            estadoText = estadoText,
            almacenText = budget.almacen?.nombre ?: "",
            fechaEmisionText = budget.fechaEmision.take(10),
            fechaConfirmacionText = budget.fechaConfirmacion?.take(10),
            fechaVencimientoText = budget.fechaVencimiento.take(10),
            clienteText = budget.cliente?.nombre ?: "Sin Cliente",
            monedaText = budget.moneda?.nombre ?: symbol,
            showDiscountColumn = hasAnyLineDiscount,
            lines = linePresentations,
            subtotalBrutoText = subtotalBrutoText,
            descuentoLineasText = descuentoLineasText,
            descuentoGlobalText = descuentoGlobalText,
            subtotalNetoText = "$symbol ${formatMoney(subtotalNetoValue)}",
            ivaTotalText = "$symbol ${formatMoney(ivaTotalValue)}",
            ivaBreakdown = ivaBreakdownPresentations,
            ajusteRedondeoText = ajusteRedondeoText,
            importeTotalText = "$symbol ${formatMoney(totalValue)}",
            terminoCondicionesText = budget.terminoCondiciones?.takeIf { it.isNotBlank() },
            notaText = budget.nota?.takeIf { it.isNotBlank() }
        )
    }
}

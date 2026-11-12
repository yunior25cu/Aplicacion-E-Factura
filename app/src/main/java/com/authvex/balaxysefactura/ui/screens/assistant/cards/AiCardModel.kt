package com.authvex.balaxysefactura.ui.screens.assistant.cards

import com.authvex.balaxysefactura.core.network.AiTarjeta
import kotlinx.serialization.json.*
import java.text.NumberFormat
import java.util.Locale

enum class AlertSeverity { CRITICAL, WARNING }

data class KpiItem(
    val label: String,
    val value: String
)

data class SummaryItem(
    val label: String,
    val value: String
)

data class AlertItem(
    val text: String,
    val severity: AlertSeverity
)

data class AiCardTable(
    val headers: List<String>,
    val rawHeaderKeys: List<String>,
    val rows: List<List<String>>,
    val footerText: String? = null
)

data class AiConfirmAction(
    val herramienta: String,
    val argumentos: JsonElement?
)

data class AiCardModel(
    val tool: String,
    val title: String,
    val kpis: List<KpiItem> = emptyList(),
    val summaries: List<SummaryItem> = emptyList(),
    val alerts: List<AlertItem> = emptyList(),
    val table: AiCardTable? = null,
    val erpUrl: String? = null,
    val confirmAction: AiConfirmAction? = null
)

private val hiddenKeyRegex = Regex("(?i)^(titulo|token_confirmacion|confirmar_con|url_erp|id(_.*)?)$")
private val alertKeySet = setOf("novedades", "alertas", "alertas_contables")

fun formatKeyLabel(key: String): String {
    val clean = key.replace('_', ' ').trim()
    if (clean.isEmpty()) return ""
    return clean.substring(0, 1).uppercase(Locale.US) + clean.substring(1)
}

fun formatEsUyNumber(number: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale("es", "UY")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }
    return nf.format(number)
}

fun isHiddenKey(key: String): Boolean {
    return hiddenKeyRegex.matches(key)
}

fun buildCard(tarjeta: AiTarjeta): AiCardModel {
    val tool = tarjeta.tool
    val datosObj = tarjeta.datos as? JsonObject

    val title = tarjeta.titulo
        ?: datosObj?.get("titulo")?.jsonPrimitive?.contentOrNull
        ?: "Balaxys"

    if (datosObj == null) {
        return AiCardModel(tool = tool, title = title)
    }

    val erpUrl = datosObj["url_erp"]?.jsonPrimitive?.contentOrNull

    val confirmAction = datosObj["confirmar_con"]?.let { confirmElement ->
        val confirmObj = confirmElement as? JsonObject
        val toolName = confirmObj?.get("herramienta")?.jsonPrimitive?.contentOrNull
        val args = confirmObj?.get("argumentos")
        if (toolName != null) AiConfirmAction(toolName, args) else null
    }

    val kpis = mutableListOf<KpiItem>()
    val summaries = mutableListOf<SummaryItem>()
    val alerts = mutableListOf<AlertItem>()
    var table: AiCardTable? = null

    // 1. Process Alert Arrays
    alertKeySet.forEach { alertKey ->
        val alertArray = datosObj[alertKey] as? JsonArray
        alertArray?.forEach { elem ->
            if (alerts.size < 12) {
                if (elem is JsonObject) {
                    val txt = elem["titulo"]?.jsonPrimitive?.contentOrNull
                        ?: elem["mensaje"]?.jsonPrimitive?.contentOrNull
                        ?: elem["texto"]?.jsonPrimitive?.contentOrNull
                        ?: elem.toString()
                    val grav = elem["gravedad"]?.jsonPrimitive?.contentOrNull ?: ""
                    val sev = if (grav.equals("critica", ignoreCase = true) || grav.equals("crítica", ignoreCase = true)) {
                        AlertSeverity.CRITICAL
                    } else {
                        AlertSeverity.WARNING
                    }
                    alerts.add(AlertItem(txt, sev))
                } else if (elem is JsonPrimitive) {
                    alerts.add(AlertItem(elem.content, AlertSeverity.WARNING))
                }
            }
        }
    }

    // 2. Process Data Entries
    datosObj.entries.forEach { (key, element) ->
        if (isHiddenKey(key) || alertKeySet.contains(key)) {
            return@forEach
        }

        when (element) {
            is JsonPrimitive -> {
                if (element.doubleOrNull != null) {
                    val doubleVal = element.double
                    kpis.add(KpiItem(formatKeyLabel(key), formatEsUyNumber(doubleVal)))
                } else if (element.booleanOrNull != null) {
                    if (summaries.size < 6) {
                        val boolVal = if (element.boolean) "Sí" else "No"
                        summaries.add(SummaryItem(formatKeyLabel(key), boolVal))
                    }
                } else if (element.contentOrNull != null) {
                    if (summaries.size < 6) {
                        summaries.add(SummaryItem(formatKeyLabel(key), element.content))
                    }
                }
            }
            is JsonArray -> {
                val firstElem = element.firstOrNull()
                if (firstElem is JsonObject && table == null) {
                    // Build Table
                    val objList = element.mapNotNull { it as? JsonObject }
                    val visibleKeys = objList.flatMap { it.keys }
                        .distinct()
                        .filter { !isHiddenKey(it) }
                        .take(4)

                    val headers = visibleKeys.map { formatKeyLabel(it) }
                    val limitedRows = objList.take(20).map { obj ->
                        visibleKeys.map { k ->
                            val cellVal = obj[k]
                            when {
                                cellVal is JsonPrimitive -> {
                                    if (cellVal.doubleOrNull != null) {
                                        formatEsUyNumber(cellVal.double)
                                    } else if (cellVal.booleanOrNull != null) {
                                        if (cellVal.boolean) "Sí" else "No"
                                    } else {
                                        cellVal.content
                                    }
                                }
                                else -> cellVal?.toString() ?: ""
                            }
                        }
                    }

                    val noteInDatos = datosObj["nota"]?.jsonPrimitive?.contentOrNull
                    val footer = when {
                        noteInDatos != null -> noteInDatos
                        objList.size > 20 -> "Mostrando 20 de ${objList.size}"
                        else -> null
                    }

                    table = AiCardTable(headers, visibleKeys, limitedRows, footer)
                } else if (firstElem is JsonPrimitive && summaries.size < 6) {
                    val joinedStr = element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ")
                    summaries.add(SummaryItem(formatKeyLabel(key), joinedStr))
                }
            }
            is JsonObject -> {
                // Nested object up to level 2
                element.entries.forEach { (nestedKey, nestedElem) ->
                    if (!isHiddenKey(nestedKey) && summaries.size < 6) {
                        if (nestedElem is JsonPrimitive) {
                            val valStr = if (nestedElem.doubleOrNull != null) {
                                formatEsUyNumber(nestedElem.double)
                            } else if (nestedElem.booleanOrNull != null) {
                                if (nestedElem.boolean) "Sí" else "No"
                            } else {
                                nestedElem.content
                            }
                            summaries.add(SummaryItem("${formatKeyLabel(key)} - ${formatKeyLabel(nestedKey)}", valStr))
                        }
                    }
                }
            }
        }
    }

    return AiCardModel(
        tool = tool,
        title = title,
        kpis = kpis,
        summaries = summaries,
        alerts = alerts,
        table = table,
        erpUrl = erpUrl,
        confirmAction = confirmAction
    )
}

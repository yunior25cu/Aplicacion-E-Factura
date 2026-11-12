package com.authvex.balaxysefactura.ui.screens.assistant

import com.authvex.balaxysefactura.core.network.CfeDetailDto
import com.authvex.balaxysefactura.core.network.CobroDetailDto
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

fun sameConversationId(a: String?, b: String?): Boolean {
    val cleanA = a?.trim().orEmpty()
    val cleanB = b?.trim().orEmpty()
    if (cleanA.isEmpty() || cleanB.isEmpty()) return false
    return cleanA.equals(cleanB, ignoreCase = true)
}

fun appendRecognizedSpeech(currentInput: String, recognizedText: String?): String {
    val cleanRecognized = recognizedText?.trim().orEmpty()
    if (cleanRecognized.isEmpty()) return currentInput
    val base = currentInput.trimEnd()
    val combined = if (base.isEmpty()) {
        cleanRecognized
    } else {
        "$base $cleanRecognized"
    }
    return combined.take(10000)
}

fun parseUtcTimestampMillis(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw.trim().removeSuffix("Z").replace(' ', 'T')
    val basePart = cleaned.substringBefore('.')
    val pattern = when (basePart.length) {
        19 -> "yyyy-MM-dd'T'HH:mm:ss"
        16 -> "yyyy-MM-dd'T'HH:mm"
        10 -> "yyyy-MM-dd"
        else -> return null
    }
    return try {
        val sdf = SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        sdf.parse(basePart)?.time
    } catch (_: Exception) {
        null
    }
}

fun formatConversationDate(
    lastMessageAt: String?,
    nowMillis: Long = System.currentTimeMillis(),
    localTimeZone: TimeZone = TimeZone.getDefault()
): String {
    val msgMillis = parseUtcTimestampMillis(lastMessageAt) ?: return ""

    val msgCal = Calendar.getInstance(localTimeZone).apply { timeInMillis = msgMillis }
    val nowCal = Calendar.getInstance(localTimeZone).apply { timeInMillis = nowMillis }

    val msgYear = msgCal.get(Calendar.YEAR)
    val msgDayOfYear = msgCal.get(Calendar.DAY_OF_YEAR)

    val nowYear = nowCal.get(Calendar.YEAR)
    val nowDayOfYear = nowCal.get(Calendar.DAY_OF_YEAR)

    val yesterdayCal = (nowCal.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }
    val yesterdayYear = yesterdayCal.get(Calendar.YEAR)
    val yesterdayDayOfYear = yesterdayCal.get(Calendar.DAY_OF_YEAR)

    return when {
        msgYear == nowYear && msgDayOfYear == nowDayOfYear -> {
            val hh = msgCal.get(Calendar.HOUR_OF_DAY)
            val mm = msgCal.get(Calendar.MINUTE)
            String.format(Locale.US, "hoy %02d:%02d", hh, mm)
        }
        msgYear == yesterdayYear && msgDayOfYear == yesterdayDayOfYear -> {
            "ayer"
        }
        else -> {
            val day = msgCal.get(Calendar.DAY_OF_MONTH)
            val month = msgCal.get(Calendar.MONTH) + 1
            String.format(Locale.US, "%02d/%02d", day, month)
        }
    }
}

fun buildCfeAssistantPrompt(doc: CfeDetailDto): String {
    val cleanSerie = doc.serie?.trim()?.trimEnd('-')?.takeIf { it.isNotEmpty() }
    val cleanNumero = doc.numero?.takeIf { it > 0 }?.toString()
    val ref = when {
        cleanSerie != null && cleanNumero != null -> "$cleanSerie-$cleanNumero"
        cleanSerie != null -> cleanSerie
        cleanNumero != null -> cleanNumero
        else -> null
    }
    val receptor = doc.receptor?.trim()?.takeIf { it.isNotEmpty() }

    return buildString {
        append("Contame el estado de la factura")
        if (ref != null) {
            append(" ")
            append(ref)
        }
        if (doc.documentoId > 0) {
            append(" (id interno ")
            append(doc.documentoId)
            append(")")
        }
        if (receptor != null) {
            append(" de ")
            append(receptor)
        }
        append(": saldo pendiente, vencimiento y cobros aplicados.")
    }
}

fun buildCollectionAssistantPrompt(collection: CobroDetailDto): String {
    val ref = collection.folio?.trim()?.takeIf { it.isNotEmpty() }
        ?: collection.numero?.takeIf { it > 0 }?.toString()
    val cliente = collection.cliente?.nombre?.trim()?.takeIf { it.isNotEmpty() }

    return buildString {
        append("Resumime el cobro")
        if (ref != null) {
            append(" ")
            append(ref)
        }
        if (collection.id > 0) {
            append(" (id interno ")
            append(collection.id)
            append(")")
        }
        if (cliente != null) {
            append(" de ")
            append(cliente)
        }
        append(": qué facturas cancela y cómo se pagó.")
    }
}

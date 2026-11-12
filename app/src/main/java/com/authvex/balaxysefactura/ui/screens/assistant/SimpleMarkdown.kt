package com.authvex.balaxysefactura.ui.screens.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

sealed class MdBlock {
    data class Header(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class BulletItem(val text: String) : MdBlock()
    data class OrderedItem(val number: String, val text: String) : MdBlock()
    data class CodeBlock(val text: String) : MdBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
    object Divider : MdBlock()
}

@Composable
fun SimpleMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = parseMarkdownBlocks(content)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Header -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        text = parseInlineMarkdown(block.text),
                        style = style,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
                is MdBlock.BulletItem -> {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text("• ", fontWeight = FontWeight.Bold, color = color)
                        Text(text = parseInlineMarkdown(block.text), style = MaterialTheme.typography.bodyMedium, color = color)
                    }
                }
                is MdBlock.OrderedItem -> {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text("${block.number}. ", fontWeight = FontWeight.Bold, color = color)
                        Text(text = parseInlineMarkdown(block.text), style = MaterialTheme.typography.bodyMedium, color = color)
                    }
                }
                is MdBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF212121), shape = RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFFE0E0E0)
                        )
                    }
                }
                is MdBlock.Table -> {
                    TableBlock(headers = block.headers, rows = block.rows, textColor = color)
                }
                is MdBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TableBlock(headers: List<String>, rows: List<List<String>>, textColor: Color) {
    val scrollState = rememberScrollState()
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    val colWidths = remember(headers, rows) {
        headers.mapIndexed { colIdx, header ->
            var maxChars = header.length
            rows.forEach { row ->
                val cell = row.getOrNull(colIdx) ?: ""
                if (cell.length > maxChars) {
                    maxChars = cell.length
                }
            }
            val estimatedDp = (maxChars * 8 + 24).dp
            estimatedDp.coerceIn(56.dp, 220.dp)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(8.dp)
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(vertical = 6.dp)
            ) {
                headers.forEachIndexed { colIdx, header ->
                    val widthDp = colWidths.getOrElse(colIdx) { 90.dp }
                    Box(
                        modifier = Modifier
                            .width(widthDp)
                            .padding(horizontal = 6.dp),
                        contentAlignment = if (isNumericCell(header)) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Text(
                            text = parseInlineMarkdown(header),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }
            }

            HorizontalDivider(color = borderColor, thickness = 1.dp)

            // Body Rows
            rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    headers.indices.forEach { colIndex ->
                        val cellText = row.getOrNull(colIndex) ?: ""
                        val isNum = isNumericCell(cellText)
                        val widthDp = colWidths.getOrElse(colIndex) { 90.dp }

                        Box(
                            modifier = Modifier
                                .width(widthDp)
                                .padding(horizontal = 6.dp),
                            contentAlignment = if (isNum) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Text(
                                text = parseInlineMarkdown(cellText),
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor
                            )
                        }
                    }
                }
                if (rowIndex < rows.size - 1) {
                    HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }
        }
    }
}

fun isNumericCell(text: String): Boolean {
    val clean = text.trim()
    if (clean.isEmpty()) return false
    return clean.all { it.isDigit() || it == '$' || it == '.' || it == ',' || it == '-' || it == '+' || it == '%' || it == ' ' } && clean.any { it.isDigit() }
}

fun parseMarkdownBlocks(text: String): List<MdBlock> {
    val lines = text.lines()
    val blocks = mutableListOf<MdBlock>()

    var inCodeBlock = false
    val codeLines = mutableListOf<String>()

    val tableLines = mutableListOf<String>()

    fun flushTable() {
        if (tableLines.isEmpty()) return
        val parsedRows = tableLines.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.matches(Regex("^[\\s|:-]+$"))) {
                null // Discard separator line (|---|, |:--|--:|)
            } else {
                val rawCells = trimmed.split("|")
                val cells = if (rawCells.size >= 2 && rawCells.first().isBlank() && rawCells.last().isBlank()) {
                    rawCells.subList(1, rawCells.size - 1)
                } else {
                    rawCells
                }
                cells.map { it.trim() }
            }
        }

        if (parsedRows.isNotEmpty()) {
            val headers = parsedRows.first()
            val rows = if (parsedRows.size > 1) parsedRows.subList(1, parsedRows.size) else emptyList()
            blocks.add(MdBlock.Table(headers, rows))
        }
        tableLines.clear()
    }

    fun flushCodeBlock() {
        if (codeLines.isNotEmpty()) {
            blocks.add(MdBlock.CodeBlock(codeLines.joinToString("\n")))
            codeLines.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            flushTable()
            if (inCodeBlock) {
                flushCodeBlock()
                inCodeBlock = false
            } else {
                inCodeBlock = true
            }
            continue
        }

        if (inCodeBlock) {
            codeLines.add(line)
            continue
        }

        if (trimmed.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$"))) {
            flushTable()
            blocks.add(MdBlock.Divider)
            continue
        }

        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            tableLines.add(trimmed)
            continue
        } else if (tableLines.isNotEmpty()) {
            flushTable()
        }

        if (trimmed.startsWith("# ")) {
            blocks.add(MdBlock.Header(1, trimmed.removePrefix("# ")))
        } else if (trimmed.startsWith("## ")) {
            blocks.add(MdBlock.Header(2, trimmed.removePrefix("## ")))
        } else if (trimmed.startsWith("### ")) {
            blocks.add(MdBlock.Header(3, trimmed.removePrefix("### ")))
        } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            blocks.add(MdBlock.BulletItem(trimmed.substring(2)))
        } else if (trimmed.matches(Regex("^\\d+\\.\\s.*"))) {
            val numPrefix = trimmed.substringBefore(".")
            val itemText = trimmed.substringAfter(". ").trim()
            blocks.add(MdBlock.OrderedItem(numPrefix, itemText))
        } else if (trimmed.isNotBlank()) {
            blocks.add(MdBlock.Paragraph(line))
        }
    }

    flushTable()
    flushCodeBlock()

    return blocks
}

fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("(\\*\\*|__)(.*?)\\1|`([^`]+)`")
        val matches = regex.findAll(text)

        for (match in matches) {
            val start = match.range.first
            if (start > cursor) {
                append(text.substring(cursor, start))
            }

            val boldContent = match.groupValues[2]
            val codeContent = match.groupValues[3]

            if (boldContent.isNotEmpty()) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(boldContent)
                pop()
            } else if (codeContent.isNotEmpty()) {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22000000)))
                append(codeContent)
                pop()
            }

            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

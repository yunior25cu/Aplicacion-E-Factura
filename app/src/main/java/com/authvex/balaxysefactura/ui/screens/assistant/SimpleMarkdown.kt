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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

data class TableLayoutResult(
    val columnWidths: List<Dp>,
    val needsHorizontalScroll: Boolean
)

fun isNumericColumn(
    colIndex: Int,
    rows: List<List<String>>,
    header: String = ""
): Boolean {
    val nonBlankDataCells = rows.mapNotNull { row ->
        row.getOrNull(colIndex)?.trim()?.takeIf { it.isNotEmpty() }
    }
    if (nonBlankDataCells.isEmpty()) {
        return isNumericCell(header)
    }
    val numericCount = nonBlankDataCells.count { isNumericCell(it) }
    return numericCount * 2 > nonBlankDataCells.size
}

fun calculateTableColumnWidths(
    headerWidths: List<Dp>,
    rowWidths: List<List<Dp>>,
    isNumericColumns: List<Boolean>,
    availableWidth: Dp,
    minTextColumnWidth: Dp = 96.dp
): TableLayoutResult {
    return calculateTableColumnWidths(
        cellWidths = listOf(headerWidths) + rowWidths,
        isNumericColumns = isNumericColumns,
        availableWidth = availableWidth,
        minTextColumnWidth = minTextColumnWidth
    )
}

fun calculateTableColumnWidths(
    cellWidths: List<List<Dp>>,
    isNumericColumns: List<Boolean>,
    availableWidth: Dp,
    minTextColumnWidth: Dp = 96.dp
): TableLayoutResult {
    val colCount = maxOf(
        isNumericColumns.size,
        cellWidths.maxOfOrNull { it.size } ?: 0
    )
    if (colCount == 0 || cellWidths.isEmpty()) {
        return TableLayoutResult(emptyList(), false)
    }

    val maxMeasured = List(colCount) { col ->
        cellWidths.maxOfOrNull { row -> row.getOrElse(col) { 0.dp } } ?: 0.dp
    }

    val numericCols = (0 until colCount).filter { isNumericColumns.getOrElse(it) { false } }
    val textCols = (0 until colCount).filter { !isNumericColumns.getOrElse(it) { false } }

    val numericTotal = numericCols.fold(0.dp) { acc, col -> acc + maxMeasured[col] }

    if (textCols.isEmpty()) {
        return TableLayoutResult(
            columnWidths = maxMeasured,
            needsHorizontalScroll = numericTotal > availableWidth
        )
    }

    val textMinWidths = textCols.associateWith { col ->
        minOf(maxMeasured[col], minTextColumnWidth)
    }
    val textMinTotal = textCols.fold(0.dp) { acc, col -> acc + textMinWidths.getValue(col) }

    val resultWidths = MutableList(colCount) { col -> maxMeasured[col] }

    if (numericTotal + textMinTotal > availableWidth) {
        for (col in textCols) {
            resultWidths[col] = textMinWidths.getValue(col)
        }
        return TableLayoutResult(
            columnWidths = resultWidths,
            needsHorizontalScroll = true
        )
    }

    val remainingForText = availableWidth - numericTotal
    val totalNaturalText = textCols.fold(0.dp) { acc, col -> acc + maxMeasured[col] }

    if (totalNaturalText <= remainingForText) {
        for (col in textCols) {
            resultWidths[col] = maxMeasured[col]
        }
        return TableLayoutResult(
            columnWidths = resultWidths,
            needsHorizontalScroll = false
        )
    }

    val allocated = mutableMapOf<Int, Dp>()
    val unsettled = textCols.toMutableSet()
    var budget = remainingForText

    while (unsettled.isNotEmpty()) {
        val equalShare = budget / unsettled.size
        val satisfied = unsettled.filter { col -> maxMeasured[col] <= equalShare }
        if (satisfied.isNotEmpty()) {
            for (col in satisfied) {
                allocated[col] = maxMeasured[col]
                budget -= maxMeasured[col]
                unsettled.remove(col)
            }
            continue
        }
        val belowMin = unsettled.filter { col -> equalShare < textMinWidths.getValue(col) }
        if (belowMin.isNotEmpty()) {
            for (col in belowMin) {
                val minW = textMinWidths.getValue(col)
                allocated[col] = minW
                budget -= minW
                unsettled.remove(col)
            }
            continue
        }
        for (col in unsettled) {
            allocated[col] = equalShare
        }
        unsettled.clear()
    }

    for (col in textCols) {
        resultWidths[col] = allocated.getValue(col)
    }

    return TableLayoutResult(
        columnWidths = resultWidths,
        needsHorizontalScroll = false
    )
}

@Composable
private fun TableBlock(headers: List<String>, rows: List<List<String>>, textColor: Color) {
    val parsedHeaders = remember(headers) { headers.map { parseInlineMarkdown(it) } }
    val parsedRows = remember(rows) { rows.map { row -> row.map { parseInlineMarkdown(it) } } }

    SharedTableView(
        headers = parsedHeaders,
        rows = parsedRows,
        headerStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        cellStyle = MaterialTheme.typography.bodySmall,
        textColor = textColor
    )
}

@Composable
fun SharedTableView(
    headers: List<AnnotatedString>,
    rows: List<List<AnnotatedString>>,
    headerStyle: TextStyle,
    cellStyle: TextStyle,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val cellHorizontalPadding = 6.dp
    val tablePadding = 6.dp
    val borderWidth = 1.dp

    val isNumericColumns = remember(headers, rows) {
        val plainRows = rows.map { row -> row.map { it.text } }
        headers.indices.map { colIdx ->
            isNumericColumn(
                colIndex = colIdx,
                rows = plainRows,
                header = headers[colIdx].text
            )
        }
    }

    val headerWidths = remember(headers, headerStyle, density, textMeasurer) {
        headers.map { headerText ->
            val measured = textMeasurer.measure(
                text = headerText,
                style = headerStyle,
                softWrap = false,
                maxLines = 1
            )
            with(density) {
                measured.size.width.toDp() + (cellHorizontalPadding * 2) + 2.dp
            }
        }
    }

    val rowWidths = remember(headers.size, rows, cellStyle, density, textMeasurer) {
        rows.map { row ->
            headers.indices.map { colIdx ->
                val cellText = row.getOrNull(colIdx) ?: AnnotatedString("")
                val measured = textMeasurer.measure(
                    text = cellText,
                    style = cellStyle,
                    softWrap = false,
                    maxLines = 1
                )
                with(density) {
                    measured.size.width.toDp() + (cellHorizontalPadding * 2) + 2.dp
                }
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidth = (maxWidth - (tablePadding * 2) - (borderWidth * 2)).coerceAtLeast(0.dp)
        val layout = remember(headerWidths, rowWidths, isNumericColumns, availableWidth) {
            calculateTableColumnWidths(
                headerWidths = headerWidths,
                rowWidths = rowWidths,
                isNumericColumns = isNumericColumns,
                availableWidth = availableWidth
            )
        }
        val totalTableWidth = layout.columnWidths.fold(0.dp) { acc, w -> acc + w }

        val scrollModifier = if (layout.needsHorizontalScroll) {
            Modifier.horizontalScroll(scrollState)
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(tablePadding)
        ) {
            Column(
                modifier = scrollModifier.width(totalTableWidth)
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    headers.forEachIndexed { colIdx, header ->
                        val isNum = isNumericColumns.getOrElse(colIdx) { false }
                        val widthDp = layout.columnWidths.getOrElse(colIdx) { 96.dp }

                        Box(
                            modifier = Modifier
                                .width(widthDp)
                                .padding(horizontal = cellHorizontalPadding),
                            contentAlignment = if (isNum) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Text(
                                text = header,
                                style = headerStyle,
                                color = textColor,
                                softWrap = !isNum,
                                maxLines = if (isNum) 1 else 3,
                                overflow = if (isNum) TextOverflow.Clip else TextOverflow.Ellipsis,
                                textAlign = if (isNum) TextAlign.End else TextAlign.Start
                            )
                        }
                    }
                }

                HorizontalDivider(color = borderColor, thickness = 1.dp)

                // Body Rows
                rows.forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        headers.indices.forEach { colIdx ->
                            val cellText = row.getOrNull(colIdx) ?: AnnotatedString("")
                            val isNum = isNumericColumns.getOrElse(colIdx) { false }
                            val widthDp = layout.columnWidths.getOrElse(colIdx) { 96.dp }

                            Box(
                                modifier = Modifier
                                    .width(widthDp)
                                    .padding(horizontal = cellHorizontalPadding),
                                contentAlignment = if (isNum) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Text(
                                    text = cellText,
                                    style = cellStyle,
                                    color = textColor,
                                    softWrap = !isNum,
                                    maxLines = if (isNum) 1 else 3,
                                    overflow = if (isNum) TextOverflow.Clip else TextOverflow.Ellipsis,
                                    textAlign = if (isNum) TextAlign.End else TextAlign.Start
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

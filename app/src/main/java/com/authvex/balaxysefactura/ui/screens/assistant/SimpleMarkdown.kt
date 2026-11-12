package com.authvex.balaxysefactura.ui.screens.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SimpleMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val lines = content.lines()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var inCodeBlock = false
        val codeBlockLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // Render code block
                    CodeBlock(codeBlockLines.joinToString("\n"))
                    codeBlockLines.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                codeBlockLines.add(line)
                continue
            }

            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                // Table row
                Text(
                    text = trimmed,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = color
                )
                continue
            }

            if (trimmed.startsWith("# ")) {
                Text(
                    text = parseInlineMarkdown(trimmed.removePrefix("# ")),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            } else if (trimmed.startsWith("## ")) {
                Text(
                    text = parseInlineMarkdown(trimmed.removePrefix("## ")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            } else if (trimmed.startsWith("### ")) {
                Text(
                    text = parseInlineMarkdown(trimmed.removePrefix("### ")),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                Row(modifier = Modifier.padding(start = 8.dp)) {
                    Text("• ", fontWeight = FontWeight.Bold, color = color)
                    Text(text = parseInlineMarkdown(trimmed.substring(2)), color = color)
                }
            } else if (trimmed.matches(Regex("^\\d+\\.\\s.*"))) {
                val parts = trimmed.split(Regex("^\\d+\\.\\s"), limit = 2)
                val numPrefix = trimmed.substringBefore(".")
                Row(modifier = Modifier.padding(start = 8.dp)) {
                    Text("$numPrefix. ", fontWeight = FontWeight.Bold, color = color)
                    Text(text = parseInlineMarkdown(parts.getOrElse(1) { "" }), color = color)
                }
            } else if (trimmed.isNotBlank()) {
                Text(text = parseInlineMarkdown(line), color = color)
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun CodeBlock(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF212121), shape = RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Color(0xFFE0E0E0)
        )
    }
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

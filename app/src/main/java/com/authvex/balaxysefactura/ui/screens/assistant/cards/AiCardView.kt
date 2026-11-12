package com.authvex.balaxysefactura.ui.screens.assistant.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.authvex.balaxysefactura.ui.screens.assistant.isNumericCell

sealed class ActionExecutionState {
    object Pending : ActionExecutionState()
    object Executing : ActionExecutionState()
    data class DoneSuccess(val message: String) : ActionExecutionState()
    data class DoneError(val message: String) : ActionExecutionState()
    object Cancelled : ActionExecutionState()
}

@Composable
fun AiCardView(
    model: AiCardModel,
    executionState: ActionExecutionState,
    onExecuteConfirm: () -> Unit,
    onCancel: () -> Unit,
    onOpenLink: (ErpLinkDestination) -> Unit,
    webBaseUrl: String,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val linkDestination = remember(model.erpUrl, webBaseUrl) {
        ErpLinkResolver.resolve(model.erpUrl, webBaseUrl)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Title
            Text(
                text = model.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Alerts
            if (model.alerts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    model.alerts.forEach { alert ->
                        val isCrit = alert.severity == AlertSeverity.CRITICAL
                        val iconColor = if (isCrit) MaterialTheme.colorScheme.error else Color(0xFFF57C00)
                        val containerColor = if (isCrit) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else Color(0xFFFFF3E0)

                        Surface(
                            color = containerColor,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isCrit) Icons.Default.Error else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = alert.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // KPIs Grid (2 columns)
            if (model.kpis.isNotEmpty()) {
                val chunks = model.kpis.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunks.forEach { rowKpis ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowKpis.forEach { kpi ->
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = kpi.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = kpi.value,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            if (rowKpis.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Summaries
            if (model.summaries.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    model.summaries.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${item.label}: ",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = item.value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Table
            model.table?.let { tbl ->
                AiCardTableView(tbl)
            }

            // Link Button
            if (linkDestination !is ErpLinkDestination.Ignored) {
                TextButton(
                    onClick = { onOpenLink(linkDestination) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver en Balaxys", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Confirm Action Controls
            model.confirmAction?.let {
                when (executionState) {
                    is ActionExecutionState.Pending -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onCancel) {
                                Text("Cancelar")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { showConfirmDialog = true }) {
                                Text("Confirmar")
                            }
                        }
                    }
                    is ActionExecutionState.Executing -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ejecutando acción...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is ActionExecutionState.DoneSuccess -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = executionState.message.ifBlank { "Acción ejecutada correctamente." },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    is ActionExecutionState.DoneError -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = executionState.message.ifBlank { "Error al ejecutar acción." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    is ActionExecutionState.Cancelled -> {
                        Text(
                            text = "Cancelado. No se hizo ningún cambio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirmar acción") },
            text = { Text("¿Confirmás la acción '${model.title}'?") },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    onExecuteConfirm()
                }) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun AiCardTableView(tbl: AiCardTable) {
    val scrollState = rememberScrollState()
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    // Measure maximum width required per column across ALL rows including headers
    val colWidths = remember(tbl) {
        tbl.headers.mapIndexed { colIdx, header ->
            var maxChars = header.length
            tbl.rows.forEach { row ->
                val cell = row.getOrNull(colIdx) ?: ""
                if (cell.length > maxChars) {
                    maxChars = cell.length
                }
            }
            // Map character count to dp width (min 56dp, max 220dp)
            val estimatedDp = (maxChars * 8 + 24).dp
            estimatedDp.coerceIn(56.dp, 220.dp)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(6.dp)
        ) {
            Column {
                // Header Row
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(vertical = 6.dp)
                ) {
                    tbl.headers.forEachIndexed { colIdx, header ->
                        val isNum = isNumericCell(header)
                        val widthDp = colWidths.getOrElse(colIdx) { 90.dp }

                        Box(
                            modifier = Modifier
                                .width(widthDp)
                                .padding(horizontal = 6.dp),
                            contentAlignment = if (isNum) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Text(
                                text = header,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                HorizontalDivider(color = borderColor, thickness = 1.dp)

                // Rows
                tbl.rows.forEachIndexed { rowIndex, row ->
                    Row(modifier = Modifier.padding(vertical = 6.dp)) {
                        tbl.headers.indices.forEach { colIdx ->
                            val cellText = row.getOrNull(colIdx) ?: ""
                            val isNum = isNumericCell(cellText)
                            val widthDp = colWidths.getOrElse(colIdx) { 90.dp }

                            Box(
                                modifier = Modifier
                                    .width(widthDp)
                                    .padding(horizontal = 6.dp),
                                contentAlignment = if (isNum) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Text(
                                    text = cellText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                                )
                            }
                        }
                    }
                    if (rowIndex < tbl.rows.size - 1) {
                        HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        }

        if (!tbl.footerText.isNullOrBlank()) {
            Text(
                text = tbl.footerText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

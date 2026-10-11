package com.authvex.balaxysefactura.ui.screens.scan.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.authvex.balaxysefactura.core.network.ScanInboxItemDto
import com.authvex.balaxysefactura.core.network.ScanTarget
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanInboxScreen(
    viewModel: ScanInboxViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToStatus: (Long) -> Unit
) {
    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escaneados") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadInbox() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Target Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = viewModel.selectedTargetFilter == null,
                    onClick = { viewModel.onTargetFilterSelected(null) },
                    label = { Text("Todos", style = MaterialTheme.typography.labelMedium) }
                )
                FilterChip(
                    selected = viewModel.selectedTargetFilter == ScanTarget.SALE,
                    onClick = { viewModel.onTargetFilterSelected(ScanTarget.SALE) },
                    label = { Text("Ventas", style = MaterialTheme.typography.labelMedium) }
                )
                FilterChip(
                    selected = viewModel.selectedTargetFilter == ScanTarget.PURCHASE,
                    onClick = { viewModel.onTargetFilterSelected(ScanTarget.PURCHASE) },
                    label = { Text("Compras", style = MaterialTheme.typography.labelMedium) }
                )
            }

            // Status Filter Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ScanStatusFilter.entries) { filter ->
                    FilterChip(
                        selected = viewModel.selectedStatusFilter == filter,
                        onClick = { viewModel.onStatusFilterSelected(filter) },
                        label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            HorizontalDivider()

            // Main Content
            when (uiState) {
                is ScanInboxUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ScanInboxUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No se pudieron cargar los documentos escaneados.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(uiState.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = { viewModel.loadInbox() }) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
                is ScanInboxUiState.Success -> {
                    if (uiState.items.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = viewModel.getEmptyStateMessage(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.items, key = { it.documentId }) { item ->
                                ScanInboxCardItem(
                                    item = item,
                                    onClick = { onNavigateToStatus(item.documentId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanInboxCardItem(
    item: ScanInboxItemDto,
    onClick: () -> Unit
) {
    val isSale = item.target == 2
    val targetText = if (isSale) "VENTA" else "COMPRA"
    val docNumber = item.number?.takeIf { it.isNotBlank() } ?: "Documento #${item.documentId}"
    val dateStr = item.issueDate?.take(10) ?: item.createdAt?.take(10) ?: ""

    val (statusColor, statusLabel) = when (item.ingestionStatus) {
        1, 2, 3 -> Color(0xFF0288D1) to "En cola"
        4 -> Color(0xFFF57C00) to "Procesando"
        5 -> Color(0xFF2E7D32) to "Listo"
        6 -> Color(0xFFC62828) to "Error"
        else -> Color(0xFF757575) to "Desconocido"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = targetText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = docNumber,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (!item.partnerName.isNullOrBlank()) {
                Text(
                    text = item.partnerName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dateStr.isNotBlank()) {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                item.total?.let { tot ->
                    if (tot > 0) {
                        Text(
                            text = String.format(Locale.US, "%,.2f", tot),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

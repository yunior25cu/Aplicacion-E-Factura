package com.authvex.balaxysefactura.ui.screens.cfe.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.authvex.balaxysefactura.core.network.CfeSummaryDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CfeListScreen(
    viewModel: CfeListViewModel,
    onBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit
) {
    val uiState = viewModel.uiState
    val listState = rememberLazyListState()

    // Detectar scroll cercano al final para cargar la siguiente página
    val shouldLoadMore by remember(listState, uiState) {
        derivedStateOf {
            val successState = uiState as? CfeListUiState.Success ?: return@derivedStateOf false
            if (!successState.canLoadMore || successState.isFetchingNextPage) return@derivedStateOf false
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadNextPage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Listado de Comprobantes", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDocuments(isRefresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // Barra de Búsqueda y Filtro
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                label = { Text("Buscar por serie, número o receptor...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (viewModel.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (uiState) {
                    is CfeListUiState.LoadingInitial -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is CfeListUiState.Empty -> {
                        EmptyState(
                            query = viewModel.searchQuery,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is CfeListUiState.Error -> {
                        ErrorState(
                            message = uiState.error.getDisplayMessage(),
                            onRetry = { viewModel.loadDocuments(isRefresh = true) },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is CfeListUiState.Success -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Mostrando ${uiState.documents.size} de ${uiState.totalRecords} comprobantes",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                FilterChip(
                                    selected = viewModel.numberSortDirection == NumberSortDirection.ASC,
                                    onClick = { viewModel.toggleNumberSort() },
                                    label = {
                                        Text(
                                            text = if (viewModel.numberSortDirection == NumberSortDirection.DESC) "N° ↓" else "N° ↑",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                )
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = uiState.documents,
                                    key = { it.documentoId }
                                ) { doc ->
                                    CfeItemCard(
                                        doc = doc,
                                        onClick = { onNavigateToDetail(doc.documentoId.toLong()) }
                                    )
                                }

                                // Indicador o error de carga de siguiente página
                                if (uiState.isFetchingNextPage) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    "Cargando más comprobantes...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                if (uiState.nextPageError != null) {
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "Error al cargar más: ${uiState.nextPageError.getDisplayMessage()}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(onClick = { viewModel.retryNextPage() }) {
                                                Text("Reintentar")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CfeItemCard(doc: CfeSummaryDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = doc.serie?.take(1) ?: "C",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${doc.serie ?: ""} ${doc.numero ?: ""}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = getCfeTypeLabel(doc.cfeCode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = doc.receptor ?: "Sin receptor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1
                )
                Text(
                    text = doc.fechaEmision?.take(10) ?: "N/A",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${doc.monedaSimbolo ?: ""} ${String.format("%.2f", doc.importeTotal ?: 0.0)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                StatusChip(estado = doc.estadoCfe)
            }
        }
    }
}

private fun getCfeTypeLabel(code: Int?): String {
    return when (code) {
        101 -> "e-Ticket"
        102 -> "NC e-Ticket"
        103 -> "ND e-Ticket"
        111 -> "e-Factura"
        112 -> "NC e-Factura"
        113 -> "ND e-Factura"
        121 -> "e-Factura Exp."
        122 -> "NC e-Factura Exp."
        123 -> "ND e-Factura Exp."
        124 -> "e-Remito Exp."
        131 -> "e-Ticket CA"
        132 -> "NC e-Ticket CA"
        133 -> "ND e-Ticket CA"
        141 -> "e-Factura CA"
        142 -> "NC e-Factura CA"
        143 -> "ND e-Factura CA"
        151 -> "e-Boleta Entrada"
        152 -> "NC e-Boleta Entrada"
        153 -> "ND e-Boleta Entrada"
        181 -> "e-Remito"
        182 -> "e-Resguardo"
        else -> if (code != null) "CFE $code" else "CFE"
    }
}

@Composable
fun StatusChip(estado: Int?) {
    val (label, color) = when (estado) {
        0 -> "Sin emitir" to Color(0xFF73777F)
        1 -> "Pendiente" to Color(0xFFED6C02)
        2 -> "En proceso" to Color(0xFFED6C02)
        3 -> "Enviado" to Color(0xFF2196F3)
        4 -> "Aceptado" to Color(0xFF2E7D32)
        5 -> "Observado" to Color(0xFFED6C02)
        6 -> "Rechazado" to Color(0xFFD32F2F)
        7 -> "Contingencia" to Color(0xFFED6C02)
        8 -> "Anulado" to Color(0xFF73777F)
        else -> "Estado $estado" to Color(0xFF73777F)
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
fun EmptyState(query: String = "", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No hay comprobantes", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (query.isEmpty()) "Los comprobantes emitidos aparecerán aquí." else "No se encontraron comprobantes para '$query'",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Reintentar")
        }
    }
}

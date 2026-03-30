package com.authvex.balaxysefactura.ui.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.authvex.balaxysefactura.core.network.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Resumen", "Serie", "Clientes", "Productos", "Docs")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Informes de Ventas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAll() }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Filtros")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> ResumenSection(viewModel.resumenState)
                    1 -> SerieSection(viewModel.serieState)
                    2 -> TopListSection("Top Clientes", viewModel.clientesState) { item ->
                        val it = item as VentaClienteItemDto
                        ReportItemRow(it.denominacion, it.importeTotalBase, it.cantidadDocumentos.toDouble(), "docs")
                    }
                    3 -> TopListSection("Top Productos", viewModel.productosState) { item ->
                        val it = item as VentaProductoItemDto
                        ReportItemRow(it.denominacion, it.importeTotalBase, it.cantidad, it.unidadMedidaCodigo ?: "un")
                    }
                    4 -> TopListSection("Por Documento", viewModel.documentosState) { item ->
                        val it = item as VentaDocumentoItemDto
                        ReportItemRow(it.etiqueta, it.importeTotalBase, it.cantidadDocumentos.toDouble(), "docs")
                    }
                }
            }
        }
    }
}

@Composable
fun ResumenSection(state: ReportsUiState<VentasResumenResponse>) {
    StateWrapper(state) { data ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Periodo: ${data.periodo.fechaDesde} al ${data.periodo.fechaHasta}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard(
                        title = "Ventas Totales",
                        value = "${data.metadata.monedaBase.simbolo} ${String.format(Locale.US, "%,.2f", data.totales.importeTotalBase)}",
                        delta = data.comparacion?.variacionImportePct,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard(
                        title = "Documentos",
                        value = data.totales.cantidadDocumentos.toString(),
                        delta = data.comparacion?.variacionCantidadPct,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Ticket Promedio",
                        value = "${data.metadata.monedaBase.simbolo} ${String.format(Locale.US, "%,.2f", data.totales.ticketPromedioBase)}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun SerieSection(state: ReportsUiState<VentasSerieResponse>) {
    StateWrapper(state) { data ->
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Ventas por ${data.granularidad}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            // En una implementación real usaríamos una librería de gráficos. 
            // Aquí simulamos con barras simples para cumplir Fase 1.
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.puntos) { punto ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(punto.etiqueta, modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelSmall)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                .fillMaxWidth((punto.importeTotalBase / data.puntos.maxOf { it.importeTotalBase }).coerceIn(0.05, 1.0).toFloat())
                        )
                        Text(
                            text = String.format(Locale.US, "%.0f", punto.importeTotalBase),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun <T> TopListSection(title: String, state: ReportsUiState<T>, itemContent: @Composable (Any) -> Unit) {
    StateWrapper(state) { data ->
        val items = when (data) {
            is VentasPorClienteResponse -> data.items
            is VentasPorProductoResponse -> data.items
            is VentasPorDocumentoResponse -> data.items
            else -> emptyList()
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(items) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        itemContent(item)
                    }
                }
            }
        }
    }
}

@Composable
fun ReportItemRow(label: String, amount: Double, qty: Double, unit: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${String.format(Locale.US, "%.1f", qty)} $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = String.format(Locale.US, "$ %,.2f", amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun KpiCard(title: String, value: String, delta: Double? = null, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (delta != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(
                        imageVector = if (delta >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (delta >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${if (delta >= 0) "+" else ""}${String.format(Locale.US, "%.1f", delta)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (delta >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun <T> StateWrapper(state: ReportsUiState<T>, content: @Composable (T) -> Unit) {
    when (state) {
        is ReportsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ReportsUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        is ReportsUiState.Empty -> {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Text("No hay datos para este periodo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }
        }
        is ReportsUiState.Success -> {
            content(state.data)
        }
    }
}

package com.authvex.balaxysefactura.ui.screens.reports

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.authvex.balaxysefactura.core.network.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Resumen", "Serie", "Clientes", "Productos", "Docs")
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Informes de Ventas", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Filtrar Fechas")
                    }
                    IconButton(onClick = { viewModel.loadAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
            
            DateRangeHeader(viewModel.fechaDesde, viewModel.fechaHasta) {
                showDatePicker = true
            }

            DatePresetsRow(onPresetSelected = { viewModel.applyPreset(it) })

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
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
                    1 -> SerieSection(viewModel.serieState, viewModel.serieViewMode) { viewModel.serieViewMode = it }
                    2 -> TopListSection("Top Clientes", viewModel.clientesState, viewModel.clientesViewMode, { viewModel.clientesViewMode = it }) { item ->
                        val it = item as VentaClienteItemDto
                        ReportItemRow(it.denominacion, it.importeTotalBase, it.cantidadDocumentos.toDouble(), "docs")
                    }
                    3 -> TopListSection("Top Productos", viewModel.productosState, viewModel.productosViewMode, { viewModel.productosViewMode = it }) { item ->
                        val it = item as VentaProductoItemDto
                        ReportItemRow(it.denominacion, it.importeTotalBase, it.cantidad, it.unidadMedidaCodigo ?: "un")
                    }
                    4 -> TopListSection("Por Documento", viewModel.documentosState, viewModel.documentosViewMode, { viewModel.documentosViewMode = it }) { item ->
                        val it = item as VentaDocumentoItemDto
                        ReportItemRow(it.etiqueta, it.importeTotalBase, it.cantidadDocumentos.toDouble(), "docs")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DateRangePickerModal(
            onDismiss = { showDatePicker = false },
            onConfirm = { desde, hasta ->
                viewModel.updateDateRange(desde, hasta)
                showDatePicker = false
            }
        )
    }
}

@Composable
fun DateRangeHeader(desde: String, hasta: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$desde  —  $hasta",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DatePresetsRow(onPresetSelected: (DatePreset) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(DatePreset.values()) { preset ->
            FilterChip(
                selected = false,
                onClick = { onPresetSelected(preset) },
                label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerModal(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val startDateStr = sdf.format(Date(start))
                        val endDateStr = sdf.format(Date(end))
                        onConfirm(startDateStr, endDateStr)
                    }
                },
                enabled = dateRangePickerState.selectedEndDateMillis != null
            ) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            title = { Text("Seleccionar rango", modifier = Modifier.padding(16.dp)) },
            showModeToggle = false,
            modifier = Modifier.fillMaxWidth().height(500.dp)
        )
    }
}

@Composable
fun ResumenSection(state: ReportsUiState<VentasResumenResponse>) {
    StateWrapper(state) { data ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                KpiCard(
                    title = "Ventas Totales",
                    value = "${data.metadata.monedaBase.simbolo} ${String.format(Locale.US, "%,.2f", data.totales.importeTotalBase)}",
                    delta = data.comparacion?.variacionImportePct,
                    modifier = Modifier.fillMaxWidth()
                )
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
            if (data.comparacion != null) {
                item {
                    ComparisonBrief(data.comparacion)
                }
            }
        }
    }
}

@Composable
fun ComparisonBrief(comp: VentasComparacionDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Vs. Periodo Anterior", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "${comp.fechaDesdeAnterior} al ${comp.fechaHastaAnterior}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Delta Importe: ${if (comp.deltaImporteBase >= 0) "+" else ""}${String.format(Locale.US, "%,.2f", comp.deltaImporteBase)}",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun SerieSection(
    state: ReportsUiState<VentasSerieResponse>,
    viewMode: SerieViewMode,
    onViewModeChange: (SerieViewMode) -> Unit
) {
    StateWrapper(state) { data ->
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Ventas por ${data.granularidad}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(4.dp)) {
                    IconButton(
                        onClick = { onViewModeChange(SerieViewMode.BARS) },
                        modifier = Modifier.size(32.dp).background(if (viewMode == SerieViewMode.BARS) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(6.dp))
                    ) {
                        Icon(Icons.Default.BarChart, null, tint = if (viewMode == SerieViewMode.BARS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { onViewModeChange(SerieViewMode.LINE) },
                        modifier = Modifier.size(32.dp).background(if (viewMode == SerieViewMode.LINE) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(6.dp))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, null, tint = if (viewMode == SerieViewMode.LINE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "SerieChart"
            ) { mode ->
                if (mode == SerieViewMode.BARS) {
                    BarChart(data.puntos)
                } else {
                    LineChart(data.puntos)
                }
            }
        }
    }
}

@Composable
fun BarChart(puntos: List<VentaPuntoDto>) {
    val maxVal = puntos.maxOfOrNull { it.importeTotalBase } ?: 1.0
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(300.dp)) {
        items(puntos) { punto ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(punto.etiqueta, modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((punto.importeTotalBase / maxVal).toFloat().coerceIn(0.01f, 1.0f))
                            .background(
                                brush = Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.0f", punto.importeTotalBase),
                    modifier = Modifier.padding(start = 8.dp).width(50.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun LineChart(puntos: List<VentaPuntoDto>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    
    Box(modifier = Modifier.fillMaxWidth().height(300.dp).padding(vertical = 16.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (puntos.isEmpty()) return@Canvas
            
            val maxVal = puntos.maxOf { it.importeTotalBase }.takeIf { it > 0 } ?: 1.0
            val width = size.width
            val height = size.height
            val spacing = width / (puntos.size - 1).coerceAtLeast(1)
            
            val path = Path()
            puntos.forEachIndexed { index, punto ->
                val x = index * spacing
                val y = height - (punto.importeTotalBase / maxVal * height).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                
                drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = Offset(x, y))
            }
            
            drawPath(path = path, color = primaryColor, style = Stroke(width = 3.dp.toPx()))
            
            val fillPath = Path().apply {
                addPath(path)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(fillPath, brush = Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent)))
        }
    }
}

@Composable
fun <T> TopListSection(
    title: String, 
    state: ReportsUiState<T>, 
    viewMode: ReportViewMode,
    onViewModeChange: (ReportViewMode) -> Unit,
    itemContent: @Composable (Any) -> Unit
) {
    StateWrapper(state) { data ->
        val items = when (data) {
            is VentasPorClienteResponse -> data.items
            is VentasPorProductoResponse -> data.items
            is VentasPorDocumentoResponse -> data.items
            else -> emptyList()
        }
        
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(4.dp)) {
                    IconButton(
                        onClick = { onViewModeChange(ReportViewMode.LIST) },
                        modifier = Modifier.size(32.dp).background(if (viewMode == ReportViewMode.LIST) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(6.dp))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, null, tint = if (viewMode == ReportViewMode.LIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { onViewModeChange(ReportViewMode.CHART) },
                        modifier = Modifier.size(32.dp).background(if (viewMode == ReportViewMode.CHART) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(6.dp))
                    ) {
                        Icon(Icons.Default.PieChart, null, tint = if (viewMode == ReportViewMode.CHART) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                    }
                }
            }

            AnimatedContent(targetState = viewMode, label = "TopContent") { mode ->
                if (mode == ReportViewMode.LIST) {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(items) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    itemContent(item)
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                        SimpleHorizontalChart(items)
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleHorizontalChart(items: List<Any>) {
    val chartData = items.map {
        when (it) {
            is VentaClienteItemDto -> it.denominacion to it.importeTotalBase
            is VentaProductoItemDto -> it.denominacion to it.importeTotalBase
            is VentaDocumentoItemDto -> it.etiqueta to it.importeTotalBase
            else -> "" to 0.0
        }
    }.take(5)
    
    val maxVal = chartData.maxOfOrNull { it.second } ?: 1.0
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        chartData.forEach { (label, value) ->
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(String.format(Locale.US, "$ %,.0f", value), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth((value / maxVal).toFloat()).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}

@Composable
fun ReportItemRow(label: String, amount: Double, qty: Double, unit: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${String.format(Locale.US, "%.1f", qty)} $unit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = String.format(Locale.US, "$ %,.2f", amount),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun KpiCard(title: String, value: String, delta: Double? = null, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            
            if (delta != null) {
                val isPositive = delta >= 0
                val color = if (isPositive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                Surface(
                    modifier = Modifier.padding(top = 10.dp),
                    color = color.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Icon(
                            imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${if (isPositive) "+" else ""}${String.format(Locale.US, "%.1f", delta)}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = color,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = "---",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

@Composable
fun <T> StateWrapper(state: ReportsUiState<T>, content: @Composable (T) -> Unit) {
    when (state) {
        is ReportsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 3.dp)
            }
        }
        is ReportsUiState.Error -> {
            Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        is ReportsUiState.Empty -> {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp))
                Text("No hay datos para este periodo", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            }
        }
        is ReportsUiState.Success -> {
            content(state.data)
        }
    }
}

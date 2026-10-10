package com.authvex.balaxysefactura.ui.screens.reports.collections.aging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.authvex.balaxysefactura.ui.screens.common.DropdownSelector
import com.authvex.balaxysefactura.ui.screens.common.SharedClientSelectDialog
import com.authvex.balaxysefactura.ui.screens.reports.DatePreset
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgingReportScreen(
    viewModel: AgingReportViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState = viewModel.uiState
    var showClientDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Antigüedad de Deuda") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Filtros
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Filtros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DropdownSelector(
                            label = "Período",
                            selectedOption = viewModel.selectedPreset.label,
                            options = DatePreset.entries.map { it.label },
                            onOptionSelected = { index -> viewModel.onPresetSelected(DatePreset.entries[index]) },
                            modifier = Modifier.weight(1f)
                        )

                        DropdownSelector(
                            label = "Moneda",
                            selectedOption = viewModel.selectedMoneda?.codigo ?: "Moneda",
                            options = viewModel.tasasCambioList.map { "${it.denominacion} (${it.codigo})" },
                            onOptionSelected = { index -> viewModel.onMonedaSelected(viewModel.tasasCambioList[index]) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedButton(
                        onClick = { showClientDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(viewModel.selectedCliente?.nombre ?: "Cliente: Todos", maxLines = 1)
                    }
                }
            }

            // Contenido Principal
            when (uiState) {
                is AgingReportUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is AgingReportUiState.Error -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = uiState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadReport() }) { Text("Reintentar") }
                        }
                    }
                }
                is AgingReportUiState.Success -> {
                    val symbol = viewModel.selectedMoneda?.codigo ?: "UYU"

                    if (uiState.hasFallback) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Vencimiento estimado: Uno o más documentos no tenían vencimiento explícito y el Backend utilizó una fecha de respaldo.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Card Resumen Buckets Aging
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Tramos de Antigüedad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            HorizontalDivider()

                            AgingBucketRow("No Vencido", "$symbol ${String.format(Locale.US, "%.2f", uiState.sumNoVencido)}", uiState.sumNoVencido, uiState.grandTotal, Color(0xFF2E7D32))
                            AgingBucketRow("1 – 30 días", "$symbol ${String.format(Locale.US, "%.2f", uiState.sum1a30)}", uiState.sum1a30, uiState.totalVencido, Color(0xFF0288D1))
                            AgingBucketRow("31 – 60 días", "$symbol ${String.format(Locale.US, "%.2f", uiState.sum31a60)}", uiState.sum31a60, uiState.totalVencido, Color(0xFFF57C00))
                            AgingBucketRow("61 – 90 días", "$symbol ${String.format(Locale.US, "%.2f", uiState.sum61a90)}", uiState.sum61a90, uiState.totalVencido, Color(0xFFE65100))
                            AgingBucketRow("+91 días", "$symbol ${String.format(Locale.US, "%.2f", uiState.sum91Mas)}", uiState.sum91Mas, uiState.totalVencido, Color(0xFFC62828))

                            HorizontalDivider()

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Vencido:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("$symbol ${String.format(Locale.US, "%.2f", uiState.totalVencido)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    // Card Ranking Clientes Mayor Deuda Vencida
                    if (uiState.clientRanking.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Clientes con Mayor Deuda Vencida (Top 10)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                HorizontalDivider()

                                uiState.clientRanking.forEachIndexed { index, item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${index + 1}. ${item.clientName}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Tramo mayor: ${item.mainBucketLabel} ($symbol ${String.format(Locale.US, "%.2f", item.mainBucketAmount)})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }

                                        Text(
                                            text = "$symbol ${String.format(Locale.US, "%.2f", item.totalVencido)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClientDialog) {
        SharedClientSelectDialog(
            clientes = viewModel.clientesList,
            isSearching = false,
            onSearch = { viewModel.onClientQueryChanged(it) },
            onSelect = {
                viewModel.onClienteSelected(it)
                showClientDialog = false
            },
            onDismiss = { showClientDialog = false }
        )
    }
}

@Composable
private fun AgingBucketRow(label: String, valueStr: String, amount: Double, totalDenominator: Double, barColor: Color) {
    val progress = if (totalDenominator > 0 && amount > 0) (amount / totalDenominator).toFloat() else 0f

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(valueStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        if (progress > 0) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = barColor,
                trackColor = barColor.copy(alpha = 0.2f)
            )
        }
    }
}

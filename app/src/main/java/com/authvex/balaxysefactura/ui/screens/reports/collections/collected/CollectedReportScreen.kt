package com.authvex.balaxysefactura.ui.screens.reports.collections.collected

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun CollectedReportScreen(
    viewModel: CollectedReportViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCollectionDetail: (Long) -> Unit
) {
    val uiState = viewModel.uiState
    var showClientDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cobros Realizados") },
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
                is CollectedReportUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is CollectedReportUiState.Error -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = uiState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadReport() }) { Text("Reintentar") }
                        }
                    }
                }
                is CollectedReportUiState.Success -> {
                    val symbol = viewModel.selectedMoneda?.codigo ?: "UYU"

                    // Card KPIs Resumen
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Resumen del Período", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            HorizontalDivider()
                            KpiRow("Total Cobrado:", "$symbol ${String.format(Locale.US, "%.2f", uiState.totalCobrado)}", isBold = true)
                            KpiRow("Cantidad de Cobros:", "${uiState.cantidadCobros}")
                            KpiRow("Ticket Promedio:", "$symbol ${String.format(Locale.US, "%.2f", uiState.ticketPromedio)}")
                        }
                    }

                    // Lista Ultimos Cobros
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Cobros Confirmados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            if (uiState.items.isEmpty()) {
                                Text(
                                    text = "No se registraron cobros confirmados en el período.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            } else {
                                val isBase = viewModel.companyBaseCurrencyId == null || viewModel.selectedMoneda?.id == viewModel.companyBaseCurrencyId
                                uiState.items.take(10).forEach { item ->
                                    val amount = if (isBase) (item.montoTotalBase ?: item.total) else (item.montoTotalOriginal?.takeIf { it > 0 } ?: item.total)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToCollectionDetail(item.id) },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.folio?.takeIf { it.isNotBlank() } ?: "Cobro N° ${item.numero ?: item.id}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${item.clienteNombre ?: item.cliente?.nombre ?: "Cliente"} - ${item.fechaConfirmacion?.take(10) ?: item.fechaEmision.take(10)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }

                                        Text(
                                            text = "$symbol ${String.format(Locale.US, "%.2f", amount)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
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
private fun KpiRow(label: String, value: String, isBold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium, color = if (isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

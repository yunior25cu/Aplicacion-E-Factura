package com.authvex.balaxysefactura.ui.screens.reports.collections.receivables

import androidx.compose.foundation.background
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
fun ReceivablesReportScreen(
    viewModel: ReceivablesReportViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState = viewModel.uiState
    var showClientDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estado de Cuentas por Cobrar") },
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
                            options = DatePreset.values().map { it.label },
                            onOptionSelected = { index -> viewModel.onPresetSelected(DatePreset.values()[index]) },
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

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DropdownSelector(
                            label = "Estado",
                            selectedOption = when (viewModel.selectedStateFilter) {
                                1 -> "Por cobrar"
                                2 -> "Cobrado"
                                else -> "Todos"
                            },
                            options = listOf("Todos", "Por cobrar", "Cobrado"),
                            onOptionSelected = { index ->
                                val st = when (index) {
                                    1 -> 1
                                    2 -> 2
                                    else -> null
                                }
                                viewModel.onStateFilterSelected(st)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedButton(
                            onClick = { showClientDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(viewModel.selectedCliente?.nombre ?: "Cliente: Todos", maxLines = 1)
                        }
                    }
                }
            }

            // Contenido Principal
            when (uiState) {
                is ReceivablesReportUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ReceivablesReportUiState.Error -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = uiState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadReport() }) { Text("Reintentar") }
                        }
                    }
                }
                is ReceivablesReportUiState.Success -> {
                    val symbol = viewModel.selectedMoneda?.codigo ?: "UYU"

                    // Resumen KPIs
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Resumen de Cuentas por Cobrar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            HorizontalDivider()
                            KpiRow("Total Por Cobrar:", "$symbol ${String.format(Locale.US, "%.2f", uiState.totalPorCobrar)}", isBold = true)
                            KpiRow("Total Cobrado:", "$symbol ${String.format(Locale.US, "%.2f", uiState.totalCobrado)}")
                            KpiRow("Notas de Crédito:", "$symbol ${String.format(Locale.US, "%.2f", uiState.totalNC)}")
                            KpiRow("Ajustes de Saldo:", "$symbol ${String.format(Locale.US, "%.2f", uiState.totalAjustes)}")
                            if (uiState.totalSaldoAFavor > 0) {
                                KpiRow("Saldo a Favor Clientes:", "$symbol ${String.format(Locale.US, "%.2f", uiState.totalSaldoAFavor)}")
                            }
                        }
                    }

                    // Lista por Clientes
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Detalle por Cliente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            if (uiState.items.isEmpty()) {
                                Text(
                                    text = "No hay cuentas por cobrar para los filtros seleccionados.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            } else {
                                val isBase = viewModel.companyBaseCurrencyId == null || viewModel.selectedMoneda?.id == viewModel.companyBaseCurrencyId
                                uiState.items.forEach { item ->
                                    val porCobrarVal = if (isBase) item.porCobrar else item.porCobrarOriginal
                                    val cobradoVal = if (isBase) item.importeCobrado else item.importeCobradoOriginal

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.denominacionCliente ?: "Cliente N° ${item.idCliente}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Cobrado: $symbol ${String.format(Locale.US, "%.2f", cobradoVal)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }

                                        Text(
                                            text = "$symbol ${String.format(Locale.US, "%.2f", porCobrarVal)}",
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

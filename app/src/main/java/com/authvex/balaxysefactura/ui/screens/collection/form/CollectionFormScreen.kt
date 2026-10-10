package com.authvex.balaxysefactura.ui.screens.collection.form

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.authvex.balaxysefactura.ui.screens.common.DatePickerField
import com.authvex.balaxysefactura.ui.screens.common.DropdownSelector
import com.authvex.balaxysefactura.ui.screens.common.SharedClientSelectDialog
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionFormScreen(
    viewModel: CollectionFormViewModel,
    onNavigateBack: () -> Unit,
    onCollectionSaved: (Long) -> Unit
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState

    var showClientDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is CollectionFormUiState.Success) {
            Toast.makeText(context, uiState.message, Toast.LENGTH_LONG).show()
            onCollectionSaved(uiState.collectionId)
            viewModel.resetState()
        } else if (uiState is CollectionFormUiState.Error) {
            Toast.makeText(context, uiState.message, Toast.LENGTH_LONG).show()
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo Cobro") },
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
            // Card 1: Cliente
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cliente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedButton(
                        onClick = { if (!viewModel.isClientLocked) showClientDialog = true },
                        enabled = !viewModel.isClientLocked,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(viewModel.selectedCliente?.nombre ?: "Seleccionar Cliente")
                    }
                }
            }

            // Card 2: Datos del Cobro
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Datos del Cobro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DatePickerField(
                            label = "Fecha Emisión",
                            valueDateString = viewModel.fechaEmision,
                            onDateSelected = { viewModel.fechaEmision = it },
                            modifier = Modifier.weight(1f)
                        )
                        DatePickerField(
                            label = "Fecha Confirmación",
                            valueDateString = viewModel.fechaConfirmacion,
                            onDateSelected = { viewModel.onFechaConfirmacionChanged(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DropdownSelector(
                            label = "Moneda Operación",
                            selectedOption = viewModel.selectedMoneda?.let { "${it.denominacion} (${it.codigo})" } ?: "Moneda",
                            options = viewModel.tasasCambioList.map { "${it.denominacion} (${it.codigo})" },
                            onOptionSelected = { index -> viewModel.onMonedaSelected(viewModel.tasasCambioList[index]) },
                            modifier = Modifier.weight(1f)
                        )

                        val filteredCuentas = viewModel.getFilteredCuentasBanco()
                        DropdownSelector(
                            label = "Cuenta Banco / Caja",
                            selectedOption = viewModel.selectedCuentaBanco?.getDisplayLabel() ?: if (filteredCuentas.isEmpty()) "Sin cuentas para moneda" else "Cuenta",
                            options = filteredCuentas.map { it.getDisplayLabel() },
                            onOptionSelected = { index -> viewModel.onCuentaBancoSelected(filteredCuentas[index]) },
                            enabled = filteredCuentas.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    DropdownSelector(
                        label = "Forma de Pago",
                        selectedOption = viewModel.selectedFormaPago?.nombre ?: if (viewModel.selectedCuentaBanco == null) "Seleccione cuenta primero" else if (viewModel.allowedFormasPagoList.isEmpty()) "Sin formas habilitadas" else "Forma de Pago",
                        options = viewModel.allowedFormasPagoList.map { it.nombre },
                        onOptionSelected = { index -> viewModel.selectedFormaPago = viewModel.allowedFormasPagoList[index] },
                        enabled = viewModel.selectedCuentaBanco != null && viewModel.allowedFormasPagoList.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = viewModel.numeroReferencia,
                        onValueChange = { viewModel.numeroReferencia = it },
                        label = { Text("Número de Referencia") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Card 3: Facturas Pendientes
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Facturas Pendientes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    if (viewModel.pendingInvoices.isEmpty()) {
                        Text(
                            text = if (viewModel.selectedCliente == null) "Seleccione un cliente para ver sus facturas pendientes" else "El cliente no tiene facturas pendientes de cobro",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        viewModel.pendingInvoices.forEachIndexed { index, item ->
                            val symbol = viewModel.selectedMoneda?.codigo ?: "UYU"
                            val pendingAmount = item.getPendingDisplay(symbol)

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (item.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color(0xFFF5F5F5)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = item.isSelected,
                                                onCheckedChange = { checked ->
                                                    viewModel.pendingInvoices[index] = item.copy(
                                                        isSelected = checked,
                                                        montoCobrarText = if (checked) String.format(Locale.US, "%.2f", pendingAmount) else "0.0"
                                                    )
                                                }
                                            )
                                            Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = item.factura.folio ?: "Factura N° ${item.factura.numero ?: item.factura.id}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = item.factura.fechaEmision.take(10),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Saldo Pendiente: $symbol ${String.format(Locale.US, "%.2f", pendingAmount)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    if (item.isSelected) {
                                        OutlinedTextField(
                                            value = item.montoCobrarText,
                                            onValueChange = { text ->
                                                viewModel.pendingInvoices[index] = item.copy(montoCobrarText = text)
                                            },
                                            label = { Text("Importe a Cobrar ($symbol)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Card 4: Nota
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nota / Observaciones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = viewModel.nota,
                        onValueChange = { viewModel.nota = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Detalles del cobro...") }
                    )
                }
            }

            // Card 5: Resumen de Cobro
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val symbol = viewModel.selectedMoneda?.codigo ?: "UYU"
                    val totalOp = viewModel.calculateTotalOperacion()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Operación:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("$symbol ${String.format(Locale.US, "%.2f", totalOp)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Buttons: Guardar | Guardar y Confirmar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.saveCollection(andConfirm = false) },
                    enabled = !viewModel.isSubmitting,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("GUARDAR", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showConfirmDialog = true },
                    enabled = !viewModel.isSubmitting,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (viewModel.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("GUARDAR Y CONFIRMAR", fontWeight = FontWeight.Bold)
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
                viewModel.onClientSelected(it)
                showClientDialog = false
            },
            onDismiss = { showClientDialog = false }
        )
    }

    if (showConfirmDialog) {
        val symbol = viewModel.selectedMoneda?.codigo ?: "UYU"
        val totalOp = viewModel.calculateTotalOperacion()
        val numDocs = viewModel.pendingInvoices.count { it.isSelected }

        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirmar Cobro") },
            text = { Text("¿Desea confirmar el cobro por $symbol ${String.format(Locale.US, "%.2f", totalOp)} sobre $numDocs factura(s)? Esta acción afectará el saldo del cliente y la contabilidad.") },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    viewModel.saveCollection(andConfirm = true)
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

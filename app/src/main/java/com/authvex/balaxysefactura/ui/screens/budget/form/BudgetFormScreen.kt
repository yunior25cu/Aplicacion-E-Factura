package com.authvex.balaxysefactura.ui.screens.budget.form

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.authvex.balaxysefactura.core.network.CatalogoItemDto
import com.authvex.balaxysefactura.core.network.ClienteDto
import com.authvex.balaxysefactura.core.network.ProductoDto
import com.authvex.balaxysefactura.core.network.TasaCambioSimpleDto
import com.authvex.balaxysefactura.ui.screens.common.DatePickerField
import com.authvex.balaxysefactura.ui.screens.common.DropdownSelector
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetFormScreen(
    viewModel: BudgetFormViewModel,
    budgetIdToEdit: Long? = null,
    onNavigateBack: () -> Unit,
    onBudgetSaved: (Long) -> Unit
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState

    var showClientDialog by remember { mutableStateOf(false) }
    var showProductDialog by remember { mutableStateOf(false) }

    LaunchedEffect(budgetIdToEdit) {
        if (budgetIdToEdit != null && budgetIdToEdit > 0) {
            viewModel.loadBudgetForEdit(budgetIdToEdit)
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is BudgetFormUiState.Success) {
            val msg = if (uiState.isEdit) "Presupuesto actualizado correctamente" else "Presupuesto guardado correctamente"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            onBudgetSaved(uiState.budgetId)
            viewModel.resetState()
        } else if (uiState is BudgetFormUiState.Error) {
            Toast.makeText(context, uiState.message, Toast.LENGTH_LONG).show()
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (viewModel.formMode == BudgetFormMode.EDIT) "Editar Presupuesto" else "Nuevo Presupuesto")
                },
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
            // Cliente Selector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cliente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedButton(
                        onClick = { showClientDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(viewModel.selectedCliente?.nombre ?: "Seleccionar Cliente")
                    }
                }
            }

            // Configuraciones (Fechas, Almacén, Moneda)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Datos del Documento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

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

                    DatePickerField(
                        label = "Fecha Vencimiento",
                        valueDateString = viewModel.fechaVencimiento,
                        onDateSelected = { viewModel.fechaVencimiento = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Selector de Almacén y Moneda
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DropdownSelector(
                            label = "Almacén",
                            selectedOption = viewModel.selectedAlmacen?.nombre ?: "Almacén",
                            options = viewModel.almacenesList.map { it.nombre },
                            onOptionSelected = { index -> viewModel.selectedAlmacen = viewModel.almacenesList[index] },
                            modifier = Modifier.weight(1f)
                        )
                        DropdownSelector(
                            label = "Moneda",
                            selectedOption = viewModel.selectedMoneda?.let { "${it.denominacion} (${it.codigo})" } ?: "Moneda",
                            options = viewModel.tasasCambioList.map { "${it.denominacion} (${it.codigo})" },
                            onOptionSelected = { index -> viewModel.onMonedaChanged(viewModel.tasasCambioList[index]) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (viewModel.selectedMoneda?.id != viewModel.companyBaseCurrencyId) {
                        Text(
                            text = "Tasa de Cambio: ${String.format(Locale.US, "%.2f", viewModel.tasaCambio)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Precios incluyen IVA", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = viewModel.preciosIncluyenIva,
                            onCheckedChange = { viewModel.preciosIncluyenIva = it }
                        )
                    }
                }
            }

            // Productos Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Productos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Button(onClick = { showProductDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Agregar")
                        }
                    }

                    if (viewModel.lineItems.value.isEmpty()) {
                        Text(
                            text = "No se han agregado productos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        viewModel.lineItems.value.forEachIndexed { index, line ->
                            val lineTotal = line.cantidad * line.precioUnitario - line.descuento
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.openLineConfiguration(line.producto, index) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(line.producto.nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "Cant: ${line.cantidad}  |  Precio: ${viewModel.selectedMoneda?.codigo ?: "UYU"} ${String.format(Locale.US, "%.2f", line.precioUnitario)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    if (line.indicadorFacturacionC4 != null) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("C4: ${line.indicadorFacturacionC4}") },
                                            colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.secondary)
                                        )
                                    }
                                    Text(
                                        text = "Total Línea: ${viewModel.selectedMoneda?.codigo ?: "UYU"} ${String.format(Locale.US, "%.2f", lineTotal)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row {
                                    IconButton(onClick = { viewModel.openLineConfiguration(line.producto, index) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar Cantidad/Precio", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { viewModel.removeLineItem(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            // Totals Summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val symbol = viewModel.selectedMoneda?.codigo ?: "UYU"
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subtotal:", style = MaterialTheme.typography.bodyMedium)
                        Text("$symbol ${String.format(Locale.US, "%.2f", viewModel.calculateSubtotal())}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("IVA:", style = MaterialTheme.typography.bodyMedium)
                        Text("$symbol ${String.format(Locale.US, "%.2f", viewModel.calculateIva())}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("$symbol ${String.format(Locale.US, "%.2f", viewModel.calculateTotal())}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Save / Update Button
            Button(
                onClick = { viewModel.submitForm() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = uiState !is BudgetFormUiState.Loading
            ) {
                if (uiState is BudgetFormUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text(
                        text = if (viewModel.formMode == BudgetFormMode.EDIT) "Guardar Cambios" else "Guardar Presupuesto",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Client Selection Dialog
    if (showClientDialog) {
        com.authvex.balaxysefactura.ui.screens.common.SharedClientSelectDialog(
            clientes = viewModel.clientesList,
            isSearching = false,
            onSearch = { viewModel.onClientQueryChanged(it) },
            onSelect = {
                viewModel.selectedCliente = it
                showClientDialog = false
            },
            onDismiss = { showClientDialog = false }
        )
    }

    // Product Selection Dialog
    if (showProductDialog) {
        com.authvex.balaxysefactura.ui.screens.common.SharedProductSelectDialog(
            products = viewModel.productosList,
            isSearching = false,
            onSearch = { viewModel.onProductQueryChanged(it) },
            onSelect = { product ->
                showProductDialog = false
                viewModel.openLineConfiguration(product)
            },
            onDismiss = { showProductDialog = false }
        )
    }

    // Line Configuration Dialog (Quantity, Price & Indicador C4)
    if (viewModel.configuringProduct != null) {
        val prod = viewModel.configuringProduct!!
        com.authvex.balaxysefactura.ui.screens.common.SharedLineConfiguratorDialog(
            productName = prod.nombre,
            initialQuantity = viewModel.dialogQuantityText,
            initialPrice = viewModel.dialogUnitPriceText,
            currencySymbol = viewModel.selectedMoneda?.codigo ?: "UYU",
            indicadoresC4 = viewModel.indicadoresC4List,
            initialC4 = viewModel.editingLineIndex?.let { viewModel.lineItems.value.getOrNull(it)?.indicadorFacturacionC4 },
            errorMessage = viewModel.lineDialogError,
            onConfirm = { qty, price, c4 ->
                viewModel.confirmLineConfiguration(qty.toString(), price.toString(), c4)
            },
            onDismiss = { viewModel.closeLineConfiguration() }
        )
    }
}

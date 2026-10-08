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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetFormScreen(
    viewModel: BudgetFormViewModel,
    onNavigateBack: () -> Unit,
    onBudgetCreated: (Long) -> Unit
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState

    var showClientDialog by remember { mutableStateOf(false) }
    var showProductDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is BudgetFormUiState.Success) {
            Toast.makeText(context, "Presupuesto guardado correctamente", Toast.LENGTH_SHORT).show()
            onBudgetCreated(uiState.budgetId)
            viewModel.resetState()
        } else if (uiState is BudgetFormUiState.Error) {
            Toast.makeText(context, uiState.message, Toast.LENGTH_LONG).show()
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo Presupuesto") },
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
                        OutlinedTextField(
                            value = viewModel.fechaEmision,
                            onValueChange = { viewModel.fechaEmision = it },
                            label = { Text("Fecha Emisión") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = viewModel.fechaConfirmacion,
                            onValueChange = { viewModel.onFechaConfirmacionChanged(it) },
                            label = { Text("Fecha Confirmación") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

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

            // Save Button
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
                    Text("Guardar Presupuesto", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Client Selection Dialog
    if (showClientDialog) {
        ClientSelectDialog(
            clientes = viewModel.clientesList,
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
        ProductSelectDialog(
            productos = viewModel.productosList,
            onSearch = { viewModel.onProductQueryChanged(it) },
            onSelect = { product ->
                showProductDialog = false
                viewModel.openLineConfiguration(product)
            },
            onDismiss = { showProductDialog = false }
        )
    }

    // Line Configuration Dialog (Quantity & Price)
    if (viewModel.configuringProduct != null) {
        LineConfigurationDialog(
            product = viewModel.configuringProduct!!,
            quantityText = viewModel.dialogQuantityText,
            priceText = viewModel.dialogUnitPriceText,
            currencySymbol = viewModel.selectedMoneda?.codigo ?: "UYU",
            errorMessage = viewModel.lineDialogError,
            onQuantityChange = { viewModel.dialogQuantityText = it },
            onPriceChange = { viewModel.dialogUnitPriceText = it },
            onConfirm = {
                viewModel.confirmLineConfiguration(viewModel.dialogQuantityText, viewModel.dialogUnitPriceText)
            },
            onDismiss = { viewModel.closeLineConfiguration() }
        )
    }
}

@Composable
fun DropdownSelector(
    label: String,
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(selectedOption, maxLines = 1)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ClientSelectDialog(
    clientes: List<ClienteDto>,
    onSearch: (String) -> Unit,
    onSelect: (ClienteDto) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar Cliente") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        onSearch(it)
                    },
                    placeholder = { Text("Buscar cliente...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Column(modifier = Modifier.height(250.dp).verticalScroll(rememberScrollState())) {
                    clientes.forEach { cliente ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(cliente) }
                                .padding(vertical = 8.dp)
                        ) {
                            Column {
                                Text(cliente.nombre, fontWeight = FontWeight.Bold)
                                if (!cliente.documentNumber.isNullOrBlank()) {
                                    Text("Doc: ${cliente.documentNumber}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ProductSelectDialog(
    productos: List<ProductoDto>,
    onSearch: (String) -> Unit,
    onSelect: (ProductoDto) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar Producto") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        onSearch(it)
                    },
                    placeholder = { Text("Buscar producto...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Column(modifier = Modifier.height(250.dp).verticalScroll(rememberScrollState())) {
                    productos.forEach { prod ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(prod) }
                                .padding(vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(prod.nombre, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("UYU ${String.format(Locale.US, "%.2f", prod.precio ?: 0.0)}", fontWeight = FontWeight.Medium)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun LineConfigurationDialog(
    product: ProductoDto,
    quantityText: String,
    priceText: String,
    currencySymbol: String,
    errorMessage: String?,
    onQuantityChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.nombre, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = onQuantityChange,
                    label = { Text("Cantidad") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                OutlinedTextField(
                    value = priceText,
                    onValueChange = onPriceChange,
                    label = { Text("Precio Unitario ($currencySymbol)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                val qty = quantityText.replace(',', '.').toDoubleOrNull() ?: 0.0
                val price = priceText.replace(',', '.').toDoubleOrNull() ?: 0.0
                val totalPreview = qty * price

                Text(
                    text = "Subtotal Estimado: $currencySymbol ${String.format(Locale.US, "%.2f", totalPreview)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Confirmar Línea")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

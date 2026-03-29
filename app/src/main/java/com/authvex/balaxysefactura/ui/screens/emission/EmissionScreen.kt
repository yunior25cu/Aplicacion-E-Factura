package com.authvex.balaxysefactura.ui.screens.emission

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.authvex.balaxysefactura.core.network.AppError
import com.authvex.balaxysefactura.core.network.CatalogoItemDto
import com.authvex.balaxysefactura.core.network.ClienteDto
import com.authvex.balaxysefactura.core.network.ProductoDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmissionScreen(
    viewModel: EmissionViewModel,
    onBack: () -> Unit,
    onNavigateToDetail: (Int) -> Unit
) {
    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emitir Documento") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState) {
                is EmissionUiState.LoadingCatalogs -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is EmissionUiState.Processing -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = uiState.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is EmissionUiState.Error -> {
                    ErrorState(error = uiState.error, onRetry = { viewModel.loadCatalogs() })
                }
                is EmissionUiState.Success -> {
                    SuccessState(
                        message = uiState.message,
                        onViewDetail = { onNavigateToDetail(uiState.documentoId.toInt()) },
                        onNew = { viewModel.resetToForm() }
                    )
                }
                is EmissionUiState.FormReady -> {
                    EmissionForm(viewModel, uiState)
                }
                else -> {}
            }
        }
    }
}

@Composable
fun EmissionForm(viewModel: EmissionViewModel, data: EmissionUiState.FormReady) {
    var showClienteDialog by remember { mutableStateOf(false) }
    var showProductoDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionTitle("Intención Fiscal")
                FiscalIntentBlock(viewModel)
            }

            item {
                SectionTitle("Datos del Documento")
                
                OutlinedButton(
                    onClick = { showClienteDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = viewModel.selectedCliente?.nombre ?: "Seleccionar Cliente")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector(
                        label = "Moneda",
                        selected = viewModel.selectedMoneda,
                        items = data.monedas,
                        onSelected = { viewModel.selectedMoneda = it },
                        modifier = Modifier.weight(1f)
                    )
                    DropdownSelector(
                        label = "Almacén",
                        selected = viewModel.selectedAlmacen,
                        items = data.almacenes,
                        onSelected = { viewModel.selectedAlmacen = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                DropdownSelector(
                    label = "Forma de Pago",
                    selected = viewModel.selectedFormaPago,
                    items = data.formasPago,
                    onSelected = { viewModel.selectedFormaPago = it }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Líneas")
                    IconButton(onClick = { showProductoDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Agregar Producto", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            items(viewModel.lineas) { linea ->
                LineaItem(linea = linea, onRemove = { viewModel.removeLinea(linea) })
            }

            if (viewModel.lineas.isEmpty()) {
                item {
                    Text(
                        "No hay productos seleccionados",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            
            item {
                OutlinedTextField(
                    value = viewModel.notas,
                    onValueChange = { viewModel.notas = it },
                    label = { Text("Notas") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionTitle("Resolución Fiscal")
                FiscalResolutionBlock(viewModel)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        val total = viewModel.lineas.sumOf { it.cantidad * it.precioUnitario }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Total Estimado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "${viewModel.selectedMoneda?.codigo ?: ""} ${String.format("%.2f", total)}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!viewModel.isFiscalResolved) {
            Button(
                onClick = { viewModel.startEmissionFlow() },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.selectedCliente != null && viewModel.lineas.isNotEmpty()
            ) {
                Text("CONTINUAR CON EMISIÓN")
            }
        } else {
            Button(
                onClick = { viewModel.onFinalEmitClick() },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.selectedResolution != null
            ) {
                Text("CONFIRMAR Y EMITIR")
            }
        }
    }

    if (showClienteDialog) {
        SelectionDialog(
            title = "Seleccionar Cliente",
            items = data.clientes,
            onDismiss = { showClienteDialog = false },
            onSelect = { 
                viewModel.selectedCliente = it
                showClienteDialog = false
            },
            itemLabel = { it.nombre }
        )
    }

    if (showProductoDialog) {
        SelectionDialog(
            title = "Seleccionar Producto",
            items = data.productos,
            onDismiss = { showProductoDialog = false },
            onSelect = { 
                viewModel.addLinea(it, 1.0)
                showProductoDialog = false
            },
            itemLabel = { "${it.nombre} (${it.precio ?: 0.0})" }
        )
    }
}

@Composable
fun FiscalIntentBlock(viewModel: EmissionViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FiscalIntent.values().forEach { intent ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                RadioButton(
                    selected = viewModel.selectedFiscalIntent == intent,
                    onClick = { viewModel.selectedFiscalIntent = intent },
                    enabled = !viewModel.isFiscalResolved
                )
                Text(
                    text = intent.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
fun FiscalResolutionBlock(viewModel: EmissionViewModel) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!viewModel.isFiscalResolved) {
            Text(
                text = "Punto de venta: pendiente de validar\nSerie: pendiente\nPróximo número: pendiente",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            viewModel.resolutionError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            DropdownSelectorResolution(
                label = "Punto de Venta",
                selected = viewModel.selectedResolution,
                items = viewModel.availableFiscalOptions.map { 
                    FiscalResolution(it.puntoVentaId, it.code, it.serie, it.proximoNumero, it.name)
                },
                onSelected = { viewModel.selectedResolution = it },
                itemLabel = { "Punto de venta ${it.puntoVentaId}" }
            )

            viewModel.selectedResolution?.let {
                Text(
                    text = "Serie: ${it.serie ?: "N/A"} - Próximo número: ${it.proximoNumero ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun LineaItem(linea: LineaForm, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(linea.producto.nombre, fontWeight = FontWeight.Bold)
                Text(
                    "Cant: ${linea.cantidad} x ${linea.precioUnitario}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                String.format("%.2f", linea.cantidad * linea.precioUnitario),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelector(
    label: String,
    selected: CatalogoItemDto?,
    items: List<CatalogoItemDto>,
    onSelected: (CatalogoItemDto) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.nombre ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.nombre) },
                    onClick = {
                        onSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelectorResolution(
    label: String,
    selected: FiscalResolution?,
    items: List<FiscalResolution>,
    onSelected: (FiscalResolution) -> Unit,
    itemLabel: (FiscalResolution) -> String
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected?.let { itemLabel(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        onSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun <T> SelectionDialog(
    title: String,
    items: List<T>,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    itemLabel: (T) -> String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(items) { item ->
                    TextButton(
                        onClick = { onSelect(item) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(itemLabel(item), modifier = Modifier.fillMaxWidth())
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun SuccessState(message: String, onViewDetail: () -> Unit, onNew: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("¡Operación Exitosa!", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF4CAF50))
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onViewDetail, modifier = Modifier.fillMaxWidth()) {
            Text("Ver Detalle Fiscal")
        }
        TextButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Text("Nueva Emisión")
        }
    }
}

@Composable
fun ErrorState(error: AppError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Error", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Text(error.getDisplayMessage(), style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRetry) {
            Text("Reintentar")
        }
    }
}

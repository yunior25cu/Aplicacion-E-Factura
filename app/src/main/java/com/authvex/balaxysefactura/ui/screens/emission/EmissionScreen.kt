package com.authvex.balaxysefactura.ui.screens.emission

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.authvex.balaxysefactura.core.network.*
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmissionScreen(
    viewModel: EmissionViewModel, 
    onBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit
) {
    val state = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emitir Comprobante") },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (state is EmissionUiState.SelectPOS) onBack() else viewModel.resetToStart() 
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is EmissionUiState.LoadingInitial -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is EmissionUiState.SelectPOS -> {
                    POSSelectionView(state.puntosVenta, onSelect = { viewModel.selectPOS(it) })
                }
                is EmissionUiState.SelectType -> {
                    TypeSelectionView(state.types, onSelect = { viewModel.selectFiscalType(it) })
                }
                is EmissionUiState.FillForm -> {
                    EmissionFormView(viewModel, state.type, state.catalogs)
                }
                is EmissionUiState.Processing -> {
                    ProcessingState(state.message, modifier = Modifier.align(Alignment.Center))
                }
                is EmissionUiState.Success -> {
                    SuccessView(state.documentoId, state.message, onFinish = { onNavigateToDetail(state.documentoId) })
                }
                is EmissionUiState.Error -> {
                    ErrorView(state.error, onReset = { viewModel.resetToStart() }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun POSSelectionView(pvs: List<PuntoVentaDto>, onSelect: (PuntoVentaDto) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Seleccione Punto de Venta", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
        LazyColumn {
            itemsIndexed(pvs) { _, pv ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    onClick = { onSelect(pv) }
                ) {
                    ListItemContent(
                        headline = pv.nombre,
                        supporting = "Número: ${pv.numero}",
                        trailing = { Icon(Icons.Default.ChevronRight, null) }
                    )
                }
            }
        }
    }
}

@Composable
fun TypeSelectionView(types: List<CfeFiscalDocumentAvailabilityItemDto>, onSelect: (CfeFiscalDocumentAvailabilityItemDto) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tipo de Documento", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
        LazyColumn {
            itemsIndexed(types) { _, type ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    enabled = type.habilitado,
                    onClick = { onSelect(type) }
                ) {
                    ListItemContent(
                        headline = type.name,
                        supporting = if (type.habilitado) {
                            "Serie ${type.serie ?: "-"} | Próximo: ${type.numeroActual ?: "-"}"
                        } else {
                            type.motivoNoHabilitado ?: "No disponible"
                        },
                        trailing = { if (type.habilitado) Icon(Icons.Default.ChevronRight, null) }
                    )
                }
            }
        }
    }
}

@Composable
fun EmissionFormView(viewModel: EmissionViewModel, type: CfeFiscalDocumentAvailabilityItemDto, catalogs: CatalogData) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text("${type.name} - Serie ${type.serie}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                
                ClientSelector(viewModel)
                Spacer(modifier = Modifier.height(16.dp))
                
                DropdownSelector("Moneda", catalogs.monedas, viewModel.selectedMoneda) { viewModel.selectedMoneda = it }
                Spacer(modifier = Modifier.height(8.dp))
                DropdownSelector("Almacén", catalogs.almacenes, viewModel.selectedAlmacen) { viewModel.selectedAlmacen = it }
                Spacer(modifier = Modifier.height(8.dp))
                DropdownSelector("Forma de Pago", catalogs.formasPago, viewModel.selectedFormaPago) { viewModel.selectedFormaPago = it }
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("Líneas del Documento", style = MaterialTheme.typography.titleMedium)
            }
            
            itemsIndexed(viewModel.lineas) { index, linea ->
                LineItemRow(linea, onRemove = { viewModel.removeLinea(index) })
            }
            
            item {
                OutlinedButton(
                    onClick = { viewModel.isConfiguringLine = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AGREGAR PRODUCTO")
                }
                
                OutlinedTextField(
                    value = viewModel.notas,
                    onValueChange = { viewModel.notas = it },
                    label = { Text("Notas/Observaciones") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            val total = viewModel.lineas.sumOf { (it.precioUnitario * it.cantidad) * (1 + (it.producto.tasaIva ?: 0.0)) }
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("TOTAL ESTIMADO", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${viewModel.selectedMoneda?.codigo ?: ""} ${NumberFormat.getCurrencyInstance().format(total).replace("$", "")}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { viewModel.proceedToEmission() },
                    enabled = viewModel.selectedCliente != null && viewModel.lineas.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("EMITIR")
                }
            }
        }
    }

    if (viewModel.isConfiguringLine) {
        ProductSearchAndConfigDialog(viewModel)
    }
}

@Composable
fun LineItemRow(linea: LineaForm, onRemove: () -> Unit) {
    val total = (linea.precioUnitario * linea.cantidad) * (1 + (linea.producto.tasaIva ?: 0.0))
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(linea.producto.nombre, fontWeight = FontWeight.Bold)
                Text("${linea.cantidad} x ${linea.precioUnitario} (+ IVA)", style = MaterialTheme.typography.bodySmall)
                if (linea.indicadorFacturacionC4 != null) {
                    AssistChip(
                        onClick = {}, 
                        label = { Text("C4: ${linea.indicadorFacturacionC4}") },
                        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.secondary)
                    )
                }
            }
            Text(NumberFormat.getCurrencyInstance().format(total), fontWeight = FontWeight.ExtraBold)
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun SuccessView(documentoId: Long, message: String, onFinish: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(100.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("¡Emisión Exitosa!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Text("VER COMPROBANTE")
        }
    }
}

@Composable
fun ErrorView(error: AppError, onReset: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f), shape = RoundedCornerShape(24.dp), modifier = Modifier.size(80.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("!", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.displayLarge)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Error en Emisión", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(error.getDisplayMessage(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onReset, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
            Text("VOLVER AL INICIO")
        }
    }
}

@Composable
fun ProcessingState(message: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(strokeWidth = 3.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("Por favor espere un momento...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
fun ListItemContent(headline: String, supporting: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(headline, style = MaterialTheme.typography.titleMedium)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium)
            }
        }
        trailing?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelector(label: String, items: List<CatalogoItemDto>, selected: CatalogoItemDto?, onSelect: (CatalogoItemDto) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.nombre ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item.nombre) }, onClick = { onSelect(item); expanded = false })
            }
        }
    }
}

@Composable
fun ClientSelector(viewModel: EmissionViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    OutlinedCard(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, null, modifier = Modifier.padding(end = 16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(viewModel.selectedCliente?.nombre ?: "Seleccionar Cliente", style = MaterialTheme.typography.titleMedium)
                Text(viewModel.selectedCliente?.ruc ?: "Toque para buscar", style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.Search, null)
        }
    }
    
    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var query by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; viewModel.searchClients(it) },
                        label = { Text("Buscar cliente...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn {
                        itemsIndexed(viewModel.clientSearchResults) { _, client ->
                            ListItem(
                                headlineContent = { Text(client.nombre) },
                                supportingContent = { Text(client.ruc ?: "") },
                                modifier = Modifier.clickable { viewModel.selectedCliente = client; showDialog = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductSearchAndConfigDialog(viewModel: EmissionViewModel) {
    Dialog(onDismissRequest = { viewModel.cancelLineConfiguration() }) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (viewModel.productBeingConfigured == null) {
                    Text("Buscar Producto", style = MaterialTheme.typography.titleLarge)
                    var query by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; viewModel.searchProducts(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Nombre o código...") }
                    )
                    LazyColumn {
                        itemsIndexed(viewModel.productSearchResults) { _, p ->
                            ListItem(
                                headlineContent = { Text(p.nombre) },
                                supportingContent = { Text("Precio: ${p.precio}") },
                                modifier = Modifier.clickable { viewModel.startLineConfiguration(p) }
                            )
                        }
                    }
                } else {
                    LineConfigurator(viewModel)
                }
            }
        }
    }
}

@Composable
fun LineConfigurator(viewModel: EmissionViewModel) {
    val product = viewModel.productBeingConfigured!!
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf(product.precio?.toString() ?: "0") }
    var selectedC4 by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(viewModel.lineConfigurationSugerido) {
        selectedC4 = viewModel.lineConfigurationSugerido?.persistedValue
    }

    Column {
        Text(product.nombre, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Cantidad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Precio Unitario") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        
        if (viewModel.isResolvingC4) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }

        viewModel.cachedCatalogs?.let { catalogs ->
            DropdownSelector(
                label = "Indicador Facturación (C4)",
                items = catalogs.indicadoresC4.map { CatalogoItemDto(it.id, it.name) },
                selected = catalogs.indicadoresC4.find { it.id == selectedC4 }?.let { CatalogoItemDto(it.id, it.name) }
            ) { selectedC4 = it.id }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { viewModel.confirmLineConfiguration(qty.toDoubleOrNull() ?: 0.0, price.toDoubleOrNull() ?: 0.0, selectedC4) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("CONFIRMAR") }
    }
}

@Composable
fun ListItem(headlineContent: @Composable () -> Unit, supportingContent: @Composable (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            headlineContent()
            supportingContent?.invoke()
        }
    }
}

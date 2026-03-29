package com.authvex.balaxysefactura.ui.screens.emission

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.authvex.balaxysefactura.core.network.*
import java.util.Locale

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
                title = { Text("Emisión", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (uiState) {
                is EmissionUiState.LoadingInitial -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is EmissionUiState.SelectPOS -> {
                    SelectPOSView(uiState.puntosVenta) { viewModel.selectPOS(it) }
                }
                is EmissionUiState.SelectType -> {
                    SelectTypeView(uiState.types) { viewModel.selectFiscalType(it) }
                }
                is EmissionUiState.FillForm -> {
                    FillFormView(viewModel, uiState.type, uiState.catalogs)
                }
                is EmissionUiState.Processing -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(uiState.message, style = MaterialTheme.typography.titleMedium)
                    }
                }
                is EmissionUiState.Success -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("¡Éxito!", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text(uiState.message, style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { onNavigateToDetail(uiState.documentoId.toInt()) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Ver Detalle Fiscal")
                        }
                        Button(onClick = { viewModel.resetToStart() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Nueva Emisión")
                        }
                    }
                }
                is EmissionUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Error", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
                        Text(uiState.error.message ?: "Ocurrió un error", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { viewModel.resetToStart() }) {
                            Text("Volver")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectPOSView(pvs: List<PuntoVentaDto>, onSelect: (PuntoVentaDto) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Seleccione Punto de Venta", style = MaterialTheme.typography.titleLarge) }
        items(pvs) { pv ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(pv) },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Text(pv.nombre, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun SelectTypeView(types: List<CfeFiscalDocumentAvailabilityItemDto>, onSelect: (CfeFiscalDocumentAvailabilityItemDto) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Seleccione Tipo de Comprobante", style = MaterialTheme.typography.titleLarge) }
        items(types) { type ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(type) },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(type.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("Serie: ${type.serie ?: "Auto"}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillFormView(viewModel: EmissionViewModel, type: CfeFiscalDocumentAvailabilityItemDto, catalogs: CatalogData) {
    var showClienteSearch by remember { mutableStateOf(false) }
    var showProductoSearch by remember { mutableStateOf(false) }

    val isDevolucion = type.cfeCode in listOf(102, 112, 103, 113)

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Emitir ${type.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Datos de Cabecera", style = MaterialTheme.typography.titleMedium)
                        
                        OutlinedTextField(
                            value = viewModel.selectedCliente?.nombre ?: "Seleccionar Cliente...",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cliente") },
                            trailingIcon = { Icon(Icons.Default.Search, "Buscar") },
                            modifier = Modifier.fillMaxWidth().clickable { showClienteSearch = true },
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DropdownSelector("Moneda", catalogs.monedas, viewModel.selectedMoneda, { viewModel.selectedMoneda = it }, Modifier.weight(1f))
                            DropdownSelector("Almacén", catalogs.almacenes, viewModel.selectedAlmacen, { viewModel.selectedAlmacen = it }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DropdownSelector("Forma de Pago", catalogs.formasPago, viewModel.selectedFormaPago, { viewModel.selectedFormaPago = it }, Modifier.weight(1f))
                            if (catalogs.vencimientos.isNotEmpty()) {
                                DropdownSelector("Vencimiento", catalogs.vencimientos, viewModel.selectedVencimiento, { viewModel.selectedVencimiento = it }, Modifier.weight(1f))
                            }
                        }

                        if (isDevolucion) {
                            OutlinedTextField(
                                value = viewModel.idDocumentoOrigen?.toString() ?: "",
                                onValueChange = { viewModel.idDocumentoOrigen = it.toLongOrNull() },
                                label = { Text("ID Documento Origen (Requerido)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Líneas de Detalle", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Button(onClick = { showProductoSearch = true }) {
                        Icon(Icons.Default.Add, "Agregar")
                        Spacer(Modifier.width(4.dp))
                        Text("Agregar")
                    }
                }
            }

            itemsIndexed(viewModel.lineas) { index, linea ->
                LineaResumenItem(linea) { viewModel.removeLinea(index) }
            }

            item {
                OutlinedTextField(
                    value = viewModel.notas,
                    onValueChange = { viewModel.notas = it },
                    label = { Text("Notas Adicionales") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        }

        Surface(shadowElevation = 8.dp) {
            val isReady = viewModel.selectedCliente != null && viewModel.lineas.isNotEmpty() && (!isDevolucion || viewModel.idDocumentoOrigen != null)
            Button(
                onClick = { viewModel.proceedToEmission() },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = isReady
            ) {
                Text("Emitir Comprobante", modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }

    if (showClienteSearch) {
        SearchableSelectionDialog(
            title = "Buscar Cliente",
            onSearch = { viewModel.searchClients(it) },
            results = viewModel.clientSearchResults,
            isSearching = viewModel.isSearching,
            onDismiss = { showClienteSearch = false },
            onSelect = { viewModel.selectedCliente = it; showClienteSearch = false },
            itemContent = { Text(it.nombre) }
        )
    }
    
    if (showProductoSearch) {
        SearchableSelectionDialog(
            title = "Buscar Producto/Servicio",
            onSearch = { viewModel.searchProducts(it) },
            results = viewModel.productSearchResults,
            isSearching = viewModel.isSearching,
            onDismiss = { showProductoSearch = false },
            onSelect = { 
                viewModel.startLineConfiguration(it)
                showProductoSearch = false 
            },
            itemContent = {
                Column {
                    Text(it.nombre, fontWeight = FontWeight.Bold)
                    Text("Precio: ${it.precio ?: 0.0} - Stock: ${it.existencia ?: 0.0}", style = MaterialTheme.typography.bodySmall)
                }
            }
        )
    }

    if (viewModel.isConfiguringLine && viewModel.productBeingConfigured != null) {
        LineConfigurationModal(
            producto = viewModel.productBeingConfigured!!,
            isResolvingC4 = viewModel.isResolvingC4,
            sugerido = viewModel.lineConfigurationSugerido,
            indicadores = catalogs.indicadoresC4,
            onConfirm = { cant, precio, ind, indSugerido, label ->
                viewModel.confirmLineConfiguration(cant, precio, ind, indSugerido, label)
            },
            onCancel = { viewModel.cancelLineConfiguration() }
        )
    }
}

@Composable
fun LineaResumenItem(linea: LineaForm, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(linea.producto.nombre, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cant: ${linea.cantidad} x ${linea.precioUnitario}", style = MaterialTheme.typography.bodyMedium)
                Text(String.format(Locale.US, "%.2f", linea.cantidad * linea.precioUnitario), fontWeight = FontWeight.Bold)
            }
            if (linea.indicadorFacturacionC4SugeridoLabel != null) {
                Text(
                    text = "C4: ${linea.indicadorFacturacionC4SugeridoLabel}", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else if (linea.indicadorFacturacionC4 != null) {
                Text(
                    text = "C4: ${linea.indicadorFacturacionC4}", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineConfigurationModal(
    producto: ProductoDto,
    isResolvingC4: Boolean,
    sugerido: CfeFiscalIndicadorSugeridoDto?,
    indicadores: List<CfeFiscalIndicadorFacturacionDto>,
    onConfirm: (Double, Double, Int?, Int?, String?) -> Unit,
    onCancel: () -> Unit
) {
    var cantidadText by remember { mutableStateOf("1.0") }
    var precioText by remember { mutableStateOf((producto.precio ?: 0.0).toString()) }
    var selectedValue by remember { mutableStateOf<Int?>(null) }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    
    // Auto-select sugerido when it changes
    LaunchedEffect(sugerido) {
        if (sugerido != null) {
            selectedValue = sugerido.persistedValue
            selectedLabel = sugerido.label
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Configurar Línea") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(producto.nombre, fontWeight = FontWeight.Bold)
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cantidadText,
                        onValueChange = { cantidadText = it },
                        label = { Text("Cantidad") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = precioText,
                        onValueChange = { precioText = it },
                        label = { Text("Precio Unit.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                if (isResolvingC4) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resolviendo atributos fiscales...", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    // C4 selection
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedLabel ?: "Sin indicador C4",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Indicador de Facturación (C4)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sin indicador C4") },
                                onClick = { 
                                    selectedValue = null
                                    selectedLabel = null
                                    expanded = false 
                                }
                            )
                            indicadores.forEach { ind ->
                                DropdownMenuItem(
                                    text = { Text(ind.name) },
                                    onClick = { 
                                        selectedValue = ind.id
                                        selectedLabel = ind.name
                                        expanded = false 
                                    }
                                )
                            }
                        }
                    }
                    if (sugerido != null && sugerido.isAutomatic) {
                        Text(
                            text = "Sugerencia del sistema aplicada automáticamente",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val c = cantidadText.toDoubleOrNull() ?: 1.0
                    val p = precioText.toDoubleOrNull() ?: 0.0
                    onConfirm(c, p, selectedValue, sugerido?.suggestedValue, sugerido?.label)
                },
                enabled = !isResolvingC4
            ) {
                Text("Confirmar Línea")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelector(label: String, items: List<CatalogoItemDto>, selected: CatalogoItemDto?, onSelect: (CatalogoItemDto) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.nombre ?: "Seleccionar...",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.nombre) },
                    onClick = { onSelect(item); expanded = false }
                )
            }
        }
    }
}

@Composable
fun <T> SearchableSelectionDialog(
    title: String,
    onSearch: (String) -> Unit,
    results: List<T>,
    isSearching: Boolean,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    itemContent: @Composable (T) -> Unit
) {
    var query by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; onSearch(it) },
                    label = { Text("Buscar...") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    LazyColumn {
                        items(results) { item ->
                            Box(modifier = Modifier.fillMaxWidth().clickable { onSelect(item) }.padding(8.dp)) {
                                itemContent(item)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

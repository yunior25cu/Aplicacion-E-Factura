package com.authvex.balaxysefactura.ui.screens.emission

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                title = { Text("Nueva Emisión", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                    ProcessingState(uiState.message, Modifier.align(Alignment.Center))
                }
                is EmissionUiState.Success -> {
                    SuccessView(
                        message = uiState.message,
                        onViewDetail = { onNavigateToDetail(uiState.documentoId.toInt()) },
                        onNew = { viewModel.resetToStart() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is EmissionUiState.Error -> {
                    ErrorView(
                        error = uiState.error,
                        onReset = { viewModel.resetToStart() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
fun SelectPOSView(pvs: List<PuntoVentaDto>, onSelect: (PuntoVentaDto) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { 
            Text("Punto de Venta", style = MaterialTheme.typography.headlineMedium)
            Text("Seleccione la sucursal o caja desde la cual emitirá el comprobante.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
        itemsIndexed(pvs) { _, pv ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(pv) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = pv.numero.toString(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(pv.nombre, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun SelectTypeView(types: List<CfeFiscalDocumentAvailabilityItemDto>, onSelect: (CfeFiscalDocumentAvailabilityItemDto) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { 
            Text("Tipo de Documento", style = MaterialTheme.typography.headlineMedium)
            Text("Seleccione el tipo de comprobante fiscal a generar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
        itemsIndexed(types) { _, type ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = type.habilitado) { onSelect(type) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (type.habilitado) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = if (type.habilitado) 1.dp else 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = type.name, 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold,
                            color = if (type.habilitado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        if (!type.habilitado) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                                Text("No Habilitado", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Text("Serie: ${type.serie ?: "Auto"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    if (!type.habilitado && type.motivoNoHabilitado != null) {
                        Text(text = type.motivoNoHabilitado, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                    }
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
            modifier = Modifier.weight(1f).padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column {
                    Text(text = type.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text(text = "Complete la información de la línea y cabecera.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }

            // Client Selection Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showClienteSearch = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Cliente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = viewModel.selectedCliente?.nombre ?: "Seleccionar Cliente...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (viewModel.selectedCliente != null) FontWeight.Bold else FontWeight.Normal,
                                color = if (viewModel.selectedCliente != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            // Secondary Options Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DropdownSelector("Moneda", catalogs.monedas, viewModel.selectedMoneda, { viewModel.selectedMoneda = it }, Modifier.weight(1f))
                            DropdownSelector("Almacén", catalogs.almacenes, viewModel.selectedAlmacen, { viewModel.selectedAlmacen = it }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DropdownSelector("Forma Pago", catalogs.formasPago, viewModel.selectedFormaPago, { viewModel.selectedFormaPago = it }, Modifier.weight(1f))
                            if (catalogs.vencimientos.isNotEmpty()) {
                                DropdownSelector("Vencimiento", catalogs.vencimientos, viewModel.selectedVencimiento, { viewModel.selectedVencimiento = it }, Modifier.weight(1f))
                            }
                        }

                        if (isDevolucion) {
                            OutlinedTextField(
                                value = viewModel.idDocumentoOrigen?.toString() ?: "",
                                onValueChange = { viewModel.idDocumentoOrigen = it.toLongOrNull() },
                                label = { Text("ID Documento Origen") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Lines Section
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Detalle de Productos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { showProductoSearch = true }, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Add, "Agregar", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Añadir")
                    }
                }
            }

            if (viewModel.lineas.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
                            Text("No hay productos añadidos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        }
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
                    label = { Text("Observaciones / Notas") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        // Bottom Summary & Action
        Surface(shadowElevation = 16.dp, color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
                val subtotal = viewModel.lineas.sumOf { it.cantidad * it.precioUnitario }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Total Estimado", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${viewModel.selectedMoneda?.codigo ?: ""} ${String.format(Locale.US, "%.2f", subtotal)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(16.dp))
                val isReady = viewModel.selectedCliente != null && viewModel.lineas.isNotEmpty() && (!isDevolucion || viewModel.idDocumentoOrigen != null)
                Button(
                    onClick = { viewModel.proceedToEmission() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = isReady,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("EMITIR COMPROBANTE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
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
            itemContent = { Text(it.nombre, fontWeight = FontWeight.SemiBold) }
        )
    }
    
    if (showProductoSearch) {
        SearchableSelectionDialog(
            title = "Buscar Producto / Servicio",
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Código: ${it.codigo ?: "N/A"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("Precio: ${it.precio ?: 0.0}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(linea.producto.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Cant: ${linea.cantidad} x ${linea.precioUnitario}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) { 
                    Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)) 
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (linea.indicadorFacturacionC4SugeridoLabel != null) {
                    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "C4: ${linea.indicadorFacturacionC4SugeridoLabel}", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Text(
                    text = String.format(Locale.US, "%.2f", linea.cantidad * linea.precioUnitario),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
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
    
    LaunchedEffect(sugerido) {
        if (sugerido != null) {
            selectedValue = sugerido.persistedValue
            selectedLabel = sugerido.label
        }
    }

    BasicAlertDialog(
        onDismissRequest = onCancel,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(24.dp),
        content = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("Configurar Línea", style = MaterialTheme.typography.headlineSmall)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(producto.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Tasa IVA: ${(producto.tasaIva?.let { it * 100 } ?: 0.0)}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = cantidadText,
                            onValueChange = { cantidadText = it },
                            label = { Text("Cantidad") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = precioText,
                            onValueChange = { precioText = it },
                            label = { Text("Precio Unit.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    
                    if (isResolvingC4) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(
                                value = selectedLabel ?: "Sin indicador C4",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Indicador de Facturación (C4)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("Sin indicador C4") }, onClick = { selectedValue = null; selectedLabel = null; expanded = false })
                                indicadores.forEach { ind ->
                                    DropdownMenuItem(text = { Text(ind.name) }, onClick = { selectedValue = ind.id; selectedLabel = ind.name; expanded = false })
                                }
                            }
                        }
                        if (sugerido != null && sugerido.isAutomatic) {
                            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), shape = RoundedCornerShape(8.dp)) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sugerencia aplicada automáticamente.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onCancel) { Text("Cancelar") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val c = cantidadText.toDoubleOrNull() ?: 1.0
                                val p = precioText.toDoubleOrNull() ?: 0.0
                                onConfirm(c, p, selectedValue, sugerido?.suggestedValue, selectedLabel)
                            },
                            enabled = !isResolvingC4,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Confirmar")
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun SuccessView(message: String, onViewDetail: () -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Emisión Exitosa", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onViewDetail, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Text("VER DETALLE FISCAL")
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onNew) {
            Text("NUEVA EMISIÓN")
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
        Text(error.message ?: "Ocurrió un error inesperado al procesar el documento.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelector(label: String, items: List<CatalogoItemDto>, selected: CatalogoItemDto?, onSelect: (CatalogoItemDto) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(
            selected?.nombre ?: "Seleccionar...",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item.nombre) }, onClick = { onSelect(item); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(24.dp),
        content = {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; onSearch(it) },
                        label = { Text("Escriba para filtrar...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )
                    Box(modifier = Modifier.weight(1f, fill = false).heightIn(max = 400.dp)) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(results) { _, item ->
                                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(item) }.padding(12.dp)) {
                                        itemContent(item)
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cerrar") }
                }
            }
        }
    )
}

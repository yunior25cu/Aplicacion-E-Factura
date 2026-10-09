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
import com.authvex.balaxysefactura.ui.screens.common.DropdownSelector
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
                        if (state is EmissionUiState.FillForm) {
                            viewModel.resetToTypeSelection()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    EmissionFormView(viewModel, state.type, state.pos)
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
                        supporting = "Número: ${pv.numero} ${if (pv.esPredeterminado) "(Predeterminado)" else ""}",
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
fun EmissionFormView(viewModel: EmissionViewModel, type: CfeFiscalDocumentAvailabilityItemDto, pos: PuntoVentaDto) {
    val catalogs = viewModel.cachedCatalogs ?: return

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                // 1. Cabecera de Contexto Fiscal
                FiscalContextCard(pos, type)
                Spacer(modifier = Modifier.height(16.dp))

                // 2. Cliente
                ClientSelector(viewModel)
                Spacer(modifier = Modifier.height(16.dp))

                // 3. Moneda (Nuevo selector compartido de Presupuesto)
                DropdownSelector(
                    label = "Moneda",
                    selectedOption = viewModel.selectedMoneda?.let { "${it.denominacion} (${it.codigo})" } ?: "Moneda",
                    options = catalogs.tasaCambios.map { "${it.denominacion} (${it.codigo})" },
                    onOptionSelected = { index -> viewModel.selectedMoneda = catalogs.tasaCambios[index] }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Almacén (Nuevo selector compartido de Presupuesto)
                DropdownSelector(
                    label = "Almacén",
                    selectedOption = viewModel.selectedAlmacen?.nombre ?: "Almacén",
                    options = catalogs.almacenes.map { it.nombre },
                    onOptionSelected = { index -> viewModel.selectedAlmacen = catalogs.almacenes[index] }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 5. Condición de pago (Dropdown original Restaurado)
                CondicionPagoSelector(viewModel.selectedCondicionPago) { viewModel.selectedCondicionPago = it }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Líneas del Documento", style = MaterialTheme.typography.titleMedium)
            }

            // 6. Líneas del Documento
            itemsIndexed(viewModel.lineas) { index, linea ->
                LineItemRow(
                    linea = linea,
                    onEdit = { viewModel.openLineEditDialog(index) },
                    onRemove = { viewModel.removeLinea(index) }
                )
            }

            item {
                // 7. Botón AGREGAR PRODUCTO
                OutlinedButton(
                    onClick = { viewModel.isConfiguringLine = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AGREGAR PRODUCTO")
                }

                // 8. Notas/Observaciones (Posición original con max 200 + filtro emoji + contador 0/200)
                OutlinedTextField(
                    value = viewModel.notas,
                    onValueChange = { viewModel.onNotasChanged(it) },
                    label = { Text("Notas/Observaciones") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    supportingText = {
                        Text(
                            text = "${viewModel.notas.length}/200",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                )
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // 9. Footer Total estimado / Emitir
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

    if (viewModel.isConfiguringLine && viewModel.editingLineIndex == null) {
        ProductSearchAndConfigDialog(viewModel)
    }

    if (viewModel.editingLineIndex != null && viewModel.productBeingConfigured != null) {
        com.authvex.balaxysefactura.ui.screens.common.LineConfigurationDialog(
            productName = viewModel.productBeingConfigured!!.nombre,
            quantityText = viewModel.dialogQuantityText,
            priceText = viewModel.dialogUnitPriceText,
            currencySymbol = viewModel.selectedMoneda?.codigo ?: "UYU",
            errorMessage = viewModel.lineDialogError,
            onQuantityChange = { viewModel.dialogQuantityText = it },
            onPriceChange = { viewModel.dialogUnitPriceText = it },
            onConfirm = {
                viewModel.confirmLineEdit(viewModel.dialogQuantityText, viewModel.dialogUnitPriceText)
            },
            onDismiss = { viewModel.cancelLineConfiguration() }
        )
    }
}

@Composable
fun FiscalContextCard(pos: PuntoVentaDto, type: CfeFiscalDocumentAvailabilityItemDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storefront, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Punto de Venta: ${pos.nombre}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${type.name} - Serie ${type.serie ?: "-"}",
                    style = MaterialTheme.typography.bodyMedium
                )
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
                Text(viewModel.selectedCliente?.documentNumber ?: "Toque para buscar", style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.Search, null)
        }
    }

    if (showDialog) {
        LaunchedEffect(Unit) {
            viewModel.initClientSearch()
        }
        Dialog(onDismissRequest = { showDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Seleccionar Cliente", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    var query by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; viewModel.searchClients(it) },
                        label = { Text("Buscar por nombre, RUT, etc.") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = ""; viewModel.searchClients("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (viewModel.isSearching) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    }
                    if (!viewModel.isSearching && viewModel.clientSearchResults.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (query.isEmpty()) "No hay clientes registrados" else "No se encontraron clientes para '$query'",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            itemsIndexed(viewModel.clientSearchResults) { _, client ->
                                ListItem(
                                    headlineContent = { Text(client.nombre, fontWeight = FontWeight.SemiBold) },
                                    supportingContent = { Text(client.documentNumber ?: "Sin RUT/CI") },
                                    modifier = Modifier.clickable { viewModel.selectedCliente = client; showDialog = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CondicionPagoSelector(selected: CondicionPagoComercial, onSelect: (CondicionPagoComercial) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(CondicionPagoComercial.CONTADO, CondicionPagoComercial.CREDITO)

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = if (selected == CondicionPagoComercial.CONTADO) "Contado" else "Crédito",
            onValueChange = {},
            readOnly = true,
            label = { Text("Condición de pago") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(if (option == CondicionPagoComercial.CONTADO) "Contado" else "Crédito") },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

@Composable
fun LineItemRow(linea: LineaForm, onEdit: () -> Unit, onRemove: () -> Unit) {
    val total = (linea.precioUnitario * linea.cantidad) * (1 + (linea.producto.tasaIva ?: 0.0))
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onEdit() }
    ) {
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
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error) }
            }
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

@Composable
fun ProductSearchAndConfigDialog(viewModel: EmissionViewModel) {
    Dialog(onDismissRequest = { viewModel.cancelLineConfiguration() }) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (viewModel.productBeingConfigured == null) {
                    LaunchedEffect(Unit) {
                        viewModel.initProductSearch()
                    }
                    Text("Seleccionar Producto", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    var query by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; viewModel.searchProducts(it) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = ""; viewModel.searchProducts("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar por nombre, SKU o código...") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (viewModel.isSearching) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    }
                    if (!viewModel.isSearching && viewModel.productSearchResults.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (query.isEmpty()) "No hay productos disponibles" else "No se encontraron productos para '$query'",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            itemsIndexed(viewModel.productSearchResults) { _, p ->
                                ListItem(
                                    headlineContent = { Text(p.nombre, fontWeight = FontWeight.SemiBold) },
                                    supportingContent = { Text("Código: ${p.codigo ?: "-"} | Precio: $ ${p.precio ?: 0.0}") },
                                    modifier = Modifier.clickable { viewModel.startLineConfiguration(p) }
                                )
                            }
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
            val selectedC4Obj = catalogs.indicadoresC4.find { it.id == selectedC4 }
            DropdownSelector(
                label = "Indicador Facturación (C4)",
                selectedOption = selectedC4Obj?.name ?: "Indicador C4",
                options = catalogs.indicadoresC4.map { it.name },
                onOptionSelected = { index -> selectedC4 = catalogs.indicadoresC4[index].id }
            )
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

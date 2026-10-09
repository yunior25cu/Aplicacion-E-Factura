package com.authvex.balaxysefactura.ui.screens.emission

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.authvex.balaxysefactura.ui.screens.common.DatePickerField
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // SCROLLABLE FORM CONTENT (Weight 1f)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cabecera de Contexto Fiscal (Solo Lectura - Inalterada)
            FiscalContextCard(pos, type)

            // CARD DOCUMENTO ORIGEN (Solo para NC / ND: 102, 103, 112, 113)
            if (viewModel.isOriginRequired()) {
                var showOriginDialog by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Documento Origen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (viewModel.selectedOriginCfe != null) {
                                TextButton(onClick = { viewModel.clearOriginDocument() }) {
                                    Text("Quitar", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        if (viewModel.selectedOriginCfe != null) {
                            val cfe = viewModel.selectedOriginCfe!!
                            val originDoc = viewModel.selectedOriginDoc
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "${getCfeTypeLabel(cfe.cfeCode)} ${cfe.serie ?: ""}-${cfe.numero ?: cfe.documentoId}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Cliente: ${cfe.receptor ?: originDoc?.cliente?.nombre ?: "Sin cliente"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Fecha Emisión: ${cfe.fechaEmision?.take(10) ?: originDoc?.fechaEmision?.take(10) ?: "-"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Total: ${cfe.monedaSimbolo ?: "$"} ${String.format(Locale.US, "%.2f", cfe.importeTotal ?: originDoc?.importeTotalBase ?: 0.0)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    viewModel.searchOriginCfes("")
                                    showOriginDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Receipt, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Seleccionar Comprobante Origen")
                            }
                        }
                    }
                }

                if (showOriginDialog) {
                    OriginCfeSelectDialog(
                        originCode = viewModel.resolveOriginCfeCode(),
                        originCfes = viewModel.originCfeSearchResults,
                        isSearching = viewModel.isSearchingOriginCfe,
                        onSearch = { viewModel.searchOriginCfes(it) },
                        onSelect = { cfe ->
                            viewModel.selectOriginDocument(cfe)
                            showOriginDialog = false
                        },
                        onDismiss = { showOriginDialog = false }
                    )
                }
            }

            // CARD 1 — CLIENTE
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cliente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    ClientSelector(viewModel)
                }
            }

            // CARD 2 — DATOS DEL DOCUMENTO
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Datos del Documento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    // FILA 1: Fecha Emisión | Fecha Confirmación
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

                    // FILA 2: Moneda | Almacén
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DropdownSelector(
                            label = "Moneda",
                            selectedOption = viewModel.selectedMoneda?.let { "${it.denominacion} (${it.codigo})" } ?: "Moneda",
                            options = catalogs.tasaCambios.map { "${it.denominacion} (${it.codigo})" },
                            onOptionSelected = { index -> viewModel.onMonedaSelected(catalogs.tasaCambios[index]) },
                            modifier = Modifier.weight(1f)
                        )
                        DropdownSelector(
                            label = "Almacén",
                            selectedOption = viewModel.selectedAlmacen?.nombre ?: "Almacén",
                            options = catalogs.almacenes.map { it.nombre },
                            onOptionSelected = { index -> viewModel.selectedAlmacen = catalogs.almacenes[index] },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!viewModel.isBaseCurrency() && viewModel.selectedMoneda != null) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val rate = viewModel.selectedMoneda?.tasaPromedio ?: 0.0
                            val code = viewModel.selectedMoneda?.codigo ?: ""
                            Text(
                                text = "$code 1 = UYU ${String.format(Locale.US, "%.3f", rate)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (rate > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )

                            if (viewModel.exchangeRateError != null) {
                                Text(
                                    text = viewModel.exchangeRateError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            if (!viewModel.isOriginRequired() && (rate <= 0 || viewModel.exchangeRateError != null) && (viewModel.tasaCambioConfig?.syncTasaAutomatico == true || viewModel.tasaCambioConfig?.fuenteTasaCambio == "BCU")) {
                                Button(
                                    onClick = { viewModel.syncBcuRate() },
                                    enabled = !viewModel.isLoadingExchangeRate,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (viewModel.isLoadingExchangeRate) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text("Sincronizar Tasa BCU")
                                }
                            }
                        }
                    }

                    // FILA 3: Condición de pago | Precios incluyen IVA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            CondicionPagoSelector(viewModel.selectedCondicionPago) { viewModel.selectedCondicionPago = it }
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Precios incluyen IVA", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Switch(
                                checked = viewModel.preciosIncluyenIva,
                                onCheckedChange = { viewModel.preciosIncluyenIva = it }
                            )
                        }
                    }
                }
            }

            // CARD 3 — PRODUCTOS
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
                        Button(onClick = { viewModel.isConfiguringLine = true }) {
                            Icon(Icons.Default.Add, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AGREGAR")
                        }
                    }

                    if (viewModel.lineas.isEmpty()) {
                        Text(
                            text = "No se han ingresado productos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        viewModel.lineas.forEachIndexed { index, linea ->
                            LineItemRow(
                                linea = linea,
                                onEdit = { viewModel.openLineEditDialog(index) },
                                onRemove = { viewModel.removeLinea(index) }
                            )
                        }
                    }
                }
            }

            // NOTAS / OBSERVACIONES (Ubicado DESPUÉS de la Card de Productos)
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

            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // FIXED FOOTER — TOTAL ESTIMADO / EMITIR AHORA (Fuera de la Column Scrollable)
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            val total = viewModel.lineas.sumOf { (it.precioUnitario * it.cantidad) * (if (viewModel.preciosIncluyenIva) 1.0 else (1 + (it.producto.tasaIva ?: 0.0))) }
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
                    enabled = viewModel.selectedCliente != null && viewModel.lineas.isNotEmpty() && (!viewModel.isOriginRequired() || viewModel.idDocumentoOrigen != null),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("EMITIR AHORA")
                }
            }
        }
    }

    if (viewModel.isConfiguringLine && viewModel.editingLineIndex == null) {
        ProductSearchAndConfigDialog(viewModel)
    }

    if (viewModel.editingLineIndex != null && viewModel.productBeingConfigured != null) {
        val prod = viewModel.productBeingConfigured!!
        com.authvex.balaxysefactura.ui.screens.common.SharedLineConfiguratorDialog(
            productName = prod.nombre,
            initialQuantity = viewModel.dialogQuantityText,
            initialPrice = viewModel.dialogUnitPriceText,
            currencySymbol = viewModel.selectedMoneda?.codigo ?: "UYU",
            indicadoresC4 = viewModel.cachedCatalogs?.indicadoresC4 ?: emptyList(),
            initialC4 = viewModel.lineas.getOrNull(viewModel.editingLineIndex!!)?.indicadorFacturacionC4,
            isResolvingC4 = viewModel.isResolvingC4,
            errorMessage = viewModel.lineDialogError,
            onConfirm = { qty, price, c4 ->
                viewModel.confirmLineConfiguration(qty, price, c4)
            },
            onDismiss = { viewModel.cancelLineConfiguration() }
        )
    }
}

@Composable
fun OriginCfeSelectDialog(
    originCode: Int,
    originCfes: List<CfeSummaryDto>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onSelect: (CfeSummaryDto) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val label = if (originCode == 111) "e-Factura (111)" else "e-Ticket (101)"

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Seleccionar Origen ($label)", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onSearch(it)
                    },
                    label = { Text("Buscar por número, serie o receptor...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                if (!isSearching && originCfes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No se encontraron CFE origen disponibles.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        itemsIndexed(originCfes) { _, cfe ->
                            ListItemContent(
                                headline = "${getCfeTypeLabel(cfe.cfeCode)} ${cfe.serie ?: ""}-${cfe.numero ?: cfe.documentoId}",
                                supporting = "Cliente: ${cfe.receptor ?: "Sin cliente"} | Total: ${cfe.monedaSimbolo ?: "$"} ${cfe.importeTotal ?: 0.0}",
                                trailing = {
                                    Button(onClick = { onSelect(cfe) }) {
                                        Text("Seleccionar")
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
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

private fun getCfeTypeLabel(code: Int?): String {
    return when (code) {
        101 -> "e-Ticket"
        102 -> "NC e-Ticket"
        103 -> "ND e-Ticket"
        111 -> "e-Factura"
        112 -> "NC e-Factura"
        113 -> "ND e-Factura"
        121 -> "e-Factura Exp."
        122 -> "NC e-Factura Exp."
        123 -> "ND e-Factura Exp."
        124 -> "e-Remito Exp."
        131 -> "e-Ticket CA"
        132 -> "NC e-Ticket CA"
        133 -> "ND e-Ticket CA"
        141 -> "e-Factura CA"
        142 -> "NC e-Factura CA"
        143 -> "ND e-Factura CA"
        151 -> "e-Boleta Entrada"
        152 -> "NC e-Boleta Entrada"
        153 -> "ND e-Boleta Entrada"
        181 -> "e-Remito"
        182 -> "e-Resguardo"
        else -> if (code != null) "CFE $code" else "CFE"
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
    if (viewModel.productBeingConfigured == null) {
        LaunchedEffect(Unit) {
            viewModel.initProductSearch()
        }
        com.authvex.balaxysefactura.ui.screens.common.SharedProductSelectDialog(
            products = viewModel.productSearchResults,
            isSearching = viewModel.isSearching,
            onSearch = { viewModel.searchProducts(it) },
            onSelect = { p -> viewModel.startLineConfiguration(p) },
            onDismiss = { viewModel.cancelLineConfiguration() }
        )
    } else {
        val prod = viewModel.productBeingConfigured!!
        com.authvex.balaxysefactura.ui.screens.common.SharedLineConfiguratorDialog(
            productName = prod.nombre,
            initialQuantity = viewModel.dialogQuantityText,
            initialPrice = viewModel.dialogUnitPriceText,
            currencySymbol = viewModel.selectedMoneda?.codigo ?: "UYU",
            indicadoresC4 = viewModel.cachedCatalogs?.indicadoresC4 ?: emptyList(),
            initialC4 = viewModel.lineConfigurationSugerido?.persistedValue,
            isResolvingC4 = viewModel.isResolvingC4,
            errorMessage = viewModel.lineDialogError,
            onConfirm = { qty, price, c4 ->
                viewModel.confirmLineConfiguration(qty, price, c4)
            },
            onDismiss = { viewModel.cancelLineConfiguration() }
        )
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

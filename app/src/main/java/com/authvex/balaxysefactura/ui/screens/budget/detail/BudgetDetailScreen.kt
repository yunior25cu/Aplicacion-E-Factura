package com.authvex.balaxysefactura.ui.screens.budget.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.authvex.balaxysefactura.core.network.BudgetEstado
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetDetailScreen(
    viewModel: BudgetDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState
    val actionEvent = viewModel.actionEvent
    var showInvoiceDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    LaunchedEffect(actionEvent) {
        when (actionEvent) {
            is BudgetActionEvent.ConfirmedSuccess -> {
                Toast.makeText(context, actionEvent.message, Toast.LENGTH_LONG).show()
                viewModel.resetActionEvent()
            }
            is BudgetActionEvent.CancelledSuccess -> {
                Toast.makeText(context, actionEvent.message, Toast.LENGTH_LONG).show()
                viewModel.resetActionEvent()
                showCancelDialog = false
            }
            is BudgetActionEvent.InvoicedSuccess -> {
                Toast.makeText(context, actionEvent.message, Toast.LENGTH_LONG).show()
                viewModel.resetActionEvent()
                showInvoiceDialog = false
            }
            is BudgetActionEvent.ActionError -> {
                Toast.makeText(context, actionEvent.message, Toast.LENGTH_LONG).show()
                viewModel.resetActionEvent()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Presupuesto") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.sharePdf(context) },
                        enabled = !viewModel.isSharingPdf
                    ) {
                        if (viewModel.isSharingPdf) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Share, contentDescription = "Compartir Presupuesto PDF")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                is BudgetDetailUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is BudgetDetailUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadDetail() }) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
                is BudgetDetailUiState.Success -> {
                    val budget = state.budget
                    val estadoEnum = BudgetEstado.fromCode(budget.estado)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header Box with Status
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
                                    Text(
                                        text = budget.folio?.takeIf { it.isNotBlank() } ?: "Presupuesto N° ${budget.numero ?: budget.id}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )

                                    val (statusColor, statusText) = when {
                                        budget.factura != null -> Color(0xFF0288D1) to "Facturado"
                                        estadoEnum == BudgetEstado.CONFIRMADO -> Color(0xFF2E7D32) to "Confirmado"
                                        estadoEnum == BudgetEstado.ANULADO || estadoEnum == BudgetEstado.CANCELADO -> Color(0xFFC62828) to "Anulado"
                                        else -> Color(0xFFE65100) to "Sin Confirmar"
                                    }

                                    Surface(
                                        color = statusColor.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = statusText,
                                            color = statusColor,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Cliente: ${budget.cliente?.nombre ?: "No especificado"}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (!budget.cliente?.documentNumber.isNullOrBlank()) {
                                    Text(
                                        text = "RUC / Doc: ${budget.cliente?.documentNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Dates & Metadata Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Información del Documento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                HorizontalDivider()
                                DetailRow("Fecha Emisión:", budget.fechaEmision.take(10))
                                budget.fechaConfirmacion?.let { DetailRow("Fecha Confirmación:", it.take(10)) }
                                DetailRow("Fecha Vencimiento:", budget.fechaVencimiento.take(10))
                                DetailRow("Moneda:", budget.moneda?.nombre ?: "UYU")
                                DetailRow("Tasa de Cambio:", String.format(Locale.US, "%.2f", budget.tasaCambio))
                                budget.almacen?.let { DetailRow("Almacén:", it.nombre) }
                            }
                        }

                        // Items Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Líneas de Productos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                HorizontalDivider()

                                budget.documentoProductos.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.descripcion ?: item.producto?.nombre ?: "Producto",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Cant: ${item.cantidad} x ${budget.moneda?.codigo ?: "UYU"} ${String.format(Locale.US, "%.2f", item.precioBase)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                        val lineTotal = item.importeBaseConIva ?: item.importeBase ?: 0.0
                                        Text(
                                            text = "${budget.moneda?.codigo ?: "UYU"} ${String.format(Locale.US, "%.2f", lineTotal)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }

                        // Summary Totals
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val symbol = budget.moneda?.codigo ?: "UYU"
                                DetailRow("Subtotal:", "$symbol ${String.format(Locale.US, "%.2f", budget.importeBase ?: 0.0)}")
                                DetailRow("IVA:", "$symbol ${String.format(Locale.US, "%.2f", budget.iva ?: 0.0)}")
                                if ((budget.descuento) > 0) {
                                    DetailRow("Descuento:", "$symbol ${String.format(Locale.US, "%.2f", budget.descuento)}")
                                }
                                HorizontalDivider()
                                val total = budget.importeTotalBase ?: budget.importeTotalOriginal ?: 0.0
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("$symbol ${String.format(Locale.US, "%.2f", total)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        // Share PDF Action (Available for ALL states)
                        OutlinedButton(
                            onClick = { viewModel.sharePdf(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !viewModel.isSharingPdf
                        ) {
                            if (viewModel.isSharingPdf) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Compartir PDF", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Linked Factura Card if present
                        if (budget.factura != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F5FE))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Receipt, contentDescription = null, tint = Color(0xFF0288D1), modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("Factura Vinculada", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF0288D1))
                                        Text(viewModel.cfeReferenceLabel ?: "Factura CFE pendiente", style = MaterialTheme.typography.bodySmall, color = Color(0xFF01579B))
                                    }
                                }
                            }
                        }

                        // Primary Action Buttons
                        if (budget.factura == null) {
                            if (estadoEnum == BudgetEstado.SIN_CONFIRMAR) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onNavigateToEdit(budget.id) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Editar", fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = { showCancelDialog = true },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Anular", fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Button(
                                        onClick = { viewModel.confirmBudget() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = actionEvent !is BudgetActionEvent.Processing
                                    ) {
                                        if (actionEvent is BudgetActionEvent.Processing) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                                        } else {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Confirmar Presupuesto", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else if (estadoEnum == BudgetEstado.CONFIRMADO) {
                                Button(
                                    onClick = {
                                        viewModel.loadInvoiceOptions()
                                        showInvoiceDialog = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                                    enabled = actionEvent !is BudgetActionEvent.Processing
                                ) {
                                    if (actionEvent is BudgetActionEvent.Processing) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                                    } else {
                                        Icon(Icons.Default.Receipt, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Facturar Presupuesto", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Cancel Confirmation Dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Anular presupuesto", fontWeight = FontWeight.Bold) },
            text = {
                Text("¿Desea anular este presupuesto? Esta acción no podrá deshacerse desde la aplicación.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.cancelBudget() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = actionEvent !is BudgetActionEvent.Processing
                ) {
                    if (actionEvent is BudgetActionEvent.Processing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    } else {
                        Text("Anular")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Invoicing Dialog
    if (showInvoiceDialog) {
        val options = viewModel.invoiceOptions
        AlertDialog(
            onDismissRequest = { showInvoiceDialog = false },
            title = { Text("Facturar Presupuesto") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (viewModel.isOptionsLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Cargando opciones de facturación...")
                        }
                    } else if (options != null) {
                        if (options.esElectronico) {
                            Text(
                                text = "Comprobante Electrónico: ${options.tipoFiscal ?: "e-Factura"} (${options.cfeCode ?: 111})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Text("Seleccione Punto de Venta Fiscal:", style = MaterialTheme.typography.bodySmall)

                            options.puntosVenta.forEach { pv ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectedPuntoVentaId = pv.id }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = viewModel.selectedPuntoVentaId == pv.id,
                                        onClick = { viewModel.selectedPuntoVentaId = pv.id }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${pv.nombre} (N° ${pv.numero})")
                                }
                            }
                        } else {
                            Text("Se generará una Factura estándar no electrónica.")
                        }

                        OutlinedTextField(
                            value = viewModel.dialogFechaEmision,
                            onValueChange = { viewModel.dialogFechaEmision = it },
                            label = { Text("Fecha Emisión") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = viewModel.dialogFechaConfirmacion,
                            onValueChange = { viewModel.dialogFechaConfirmacion = it },
                            label = { Text("Fecha Confirmación") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.invoiceBudget(
                            fechaEmision = viewModel.dialogFechaEmision,
                            fechaConfirmacion = viewModel.dialogFechaConfirmacion,
                            puntoVentaIdFiscal = viewModel.selectedPuntoVentaId,
                            lineas = null // Full invoicing
                        )
                    },
                    enabled = !viewModel.isOptionsLoading && actionEvent !is BudgetActionEvent.Processing
                ) {
                    Text("Facturar Ahora")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInvoiceDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

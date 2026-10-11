package com.authvex.balaxysefactura.ui.screens.collection.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.authvex.balaxysefactura.core.network.CobroDetailDto
import com.authvex.balaxysefactura.core.repository.CfeRepository
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    viewModel: CollectionDetailViewModel,
    cfeRepository: CfeRepository? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.confirmMessage) {
        val msg = viewModel.confirmMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.confirmMessage = null
        }
    }

    LaunchedEffect(viewModel.receiptError) {
        val err = viewModel.receiptError
        if (!err.isNullOrBlank()) {
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.receiptError = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Cobro") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
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
            when (uiState) {
                is CollectionDetailUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is CollectionDetailUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = uiState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadDetail() }) { Text("Reintentar") }
                        }
                    }
                }
                is CollectionDetailUiState.Success -> {
                    val collection = uiState.collection
                    val isUnconfirmed = collection.estado == 1
                    val isConfirmed = collection.estado == 2

                    val (statusColor, statusText) = when (collection.estado) {
                        1 -> Color(0xFFE65100) to "Sin Confirmar"
                        2 -> Color(0xFF2E7D32) to "Confirmado"
                        3 -> Color(0xFFC62828) to "Anulado"
                        4 -> Color(0xFF757575) to "Cancelado"
                        else -> Color(0xFFE65100) to "Sin Confirmar"
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Draft Notice Banner for SinConfirmar
                        if (isUnconfirmed) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Este cobro está guardado como borrador. Las facturas y sus saldos se actualizarán al confirmarlo.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Header Card
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
                                        text = collection.folio?.takeIf { it.isNotBlank() } ?: "Cobro N° ${collection.numero ?: collection.id}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )

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
                                    text = "Cliente: ${collection.cliente?.nombre ?: "No especificado"}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (!collection.cliente?.documentNumber.isNullOrBlank()) {
                                    Text(
                                        text = "RUC / Doc: ${collection.cliente?.documentNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Document Receipt Actions for Confirmado (estado == 2)
                        if (isConfirmed) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Recibo de Cobro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    HorizontalDivider()

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.viewReceipt(context, cfeRepository) },
                                            enabled = !viewModel.isGeneratingReceipt,
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Ver", style = MaterialTheme.typography.labelMedium)
                                        }

                                        /*
                                        OutlinedButton(
                                            onClick = { viewModel.printReceipt(context, cfeRepository) },
                                            enabled = !viewModel.isGeneratingReceipt,
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Imprimir", style = MaterialTheme.typography.labelMedium)
                                        }
                                        */

                                        Button(
                                            onClick = { viewModel.shareReceipt(context, cfeRepository) },
                                            enabled = !viewModel.isGeneratingReceipt,
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            if (viewModel.isGeneratingReceipt) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                            } else {
                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Compartir", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Info Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Información del Cobro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                HorizontalDivider()
                                DetailRow("Fecha Emisión:", collection.fechaEmision.take(10))
                                collection.fechaConfirmacion?.let { DetailRow("Fecha Confirmación:", it.take(10)) }
                                DetailRow("Moneda:", collection.moneda?.nombre ?: "UYU")
                                DetailRow("Tasa de Cambio:", String.format(Locale.US, "%.2f", collection.tasaCambio))
                                collection.cuentaBanco?.let { DetailRow("Cuenta:", it.getDisplayLabel()) }
                                collection.formaPago?.let { DetailRow("Forma de Pago:", it.nombre) }
                                if (!collection.numeroReferencia.isNullOrBlank()) {
                                    DetailRow("N° Referencia:", collection.numeroReferencia)
                                }
                                if (!collection.nota.isNullOrBlank()) {
                                    DetailRow("Nota:", collection.nota)
                                }
                            }
                        }

                        // Facturas Aplicadas / Facturas a Aplicar Card
                        if (collection.facturaCobros.isNotEmpty()) {
                            val invoicesTitle = if (isUnconfirmed) "Facturas a Aplicar" else "Facturas Aplicadas"
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(invoicesTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    HorizontalDivider()

                                    collection.facturaCobros.forEach { fc ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = fc.factura.folio ?: "Factura N° ${fc.factura.id}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            val amount = if (fc.montoOriginal > 0) fc.montoOriginal else fc.montoBase
                                            val symbol = collection.moneda?.codigo ?: "UYU"
                                            Text(
                                                text = "$symbol ${String.format(Locale.US, "%.2f", amount)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
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
                                val symbol = collection.moneda?.codigo ?: "UYU"
                                val total = if (collection.montoTotalOriginal > 0) collection.montoTotalOriginal else collection.montoTotalBase
                                val totalLabel = if (isUnconfirmed) "Total del Cobro:" else "Total Cobrado:"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(totalLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("$symbol ${String.format(Locale.US, "%.2f", total)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        // Action Buttons for SinConfirmar
                        if (isUnconfirmed) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onNavigateBack,
                                    enabled = !viewModel.isConfirming,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Cerrar", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { showConfirmDialog = true },
                                    enabled = !viewModel.isConfirming,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (viewModel.isConfirming) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                    } else {
                                        Text("Confirmar", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (showConfirmDialog) {
                        val symbol = collection.moneda?.codigo ?: "UYU"
                        val total = if (collection.montoTotalOriginal > 0) collection.montoTotalOriginal else collection.montoTotalBase
                        val numFacturas = collection.facturaCobros.size
                        val numFacturasText = if (numFacturas == 1) "1 factura" else "$numFacturas facturas"
                        val folioLabel = collection.folio?.takeIf { it.isNotBlank() } ?: "N° ${collection.numero ?: collection.id}"

                        AlertDialog(
                            onDismissRequest = { showConfirmDialog = false },
                            title = { Text("Confirmar cobro") },
                            text = {
                                Text("Se confirmará el cobro $folioLabel por $symbol ${String.format(Locale.US, "%.2f", total)} y se aplicará a $numFacturasText. Esta operación actualizará los saldos financieros correspondientes.")
                            },
                            confirmButton = {
                                Button(onClick = {
                                    showConfirmDialog = false
                                    viewModel.confirmCollection()
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
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

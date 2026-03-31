package com.authvex.balaxysefactura.ui.screens.cfe.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.authvex.balaxysefactura.core.network.CfeDetailDto
import com.authvex.balaxysefactura.ui.screens.cfe.list.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CfeDetailScreen(
    viewModel: CfeDetailViewModel,
    onBack: () -> Unit
) {
    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Comprobante", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDetail() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            when (uiState) {
                is CfeDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is CfeDetailUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = uiState.error.getDisplayMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadDetail() }) {
                            Text("Reintentar")
                        }
                    }
                }
                is CfeDetailUiState.Success -> {
                    CfeDetailContent(doc = uiState.document)
                }
            }
        }
    }
}

@Composable
fun CfeDetailContent(doc: CfeDetailDto) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header Card with Main Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                StatusChip(estado = doc.estadoCfe)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${doc.serie ?: ""} ${doc.numero ?: ""}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Tipo CFE: ${getCfeTypeLabel(doc.cfeCode)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${doc.monedaSimbolo ?: ""} ${doc.importeTotal}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        DetailSectionCard(title = "Receptor") {
            DetailItemRow(label = "Nombre / Razón Social", value = doc.receptor)
            DetailItemRow(label = "Estado Receptor", value = getEstadoReceptorLabel(doc.estadoReceptor))
        }

        DetailSectionCard(title = "Cronología") {
            DetailItemRow(label = "Emisión", value = doc.fechaEmision?.take(16))
            DetailItemRow(label = "Confirmación", value = doc.fechaConfirmacion?.take(16))
            DetailItemRow(label = "Aceptado (UTC)", value = doc.fechaAceptadoUtc?.take(16))
        }

        if (!doc.ultimoError.isNullOrBlank()) {
            val isAceptado = doc.estadoCfe == 4 // Mapping oficial: 4 -> Aceptado
            val containerColor = if (isAceptado)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)

            val contentColor = if (isAceptado)
                MaterialTheme.colorScheme.onSurfaceVariant
            else
                MaterialTheme.colorScheme.error

            val title = if (isAceptado) "Último Error Histórico" else "Último Error Registrado"

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = containerColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = doc.ultimoError,
                        color = if (isAceptado) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
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

private fun getEstadoReceptorLabel(estado: Int?): String = when (estado) {
    0 -> "Sin estado"
    1 -> "Pendiente"
    2 -> "Aceptado"
    3 -> "Observado"
    4 -> "Rechazado"
    else -> "Estado $estado"
}

@Composable
fun DetailSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun DetailItemRow(label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value ?: "-", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

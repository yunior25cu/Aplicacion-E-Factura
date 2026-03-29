package com.authvex.balaxysefactura.ui.screens.cfe.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.authvex.balaxysefactura.core.network.CfeDetailDto

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
                title = { Text("Detalle CFE") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDetail() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                is CfeDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is CfeDetailUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = uiState.error.getDisplayMessage(), color = MaterialTheme.colorScheme.error)
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
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "${doc.serie ?: ""} ${doc.numero ?: ""}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                val (statusLabel, statusColor) = when (doc.estadoCfe) {
                    6 -> "Aceptado" to Color(0xFF4CAF50)
                    1 -> "Pendiente" to Color.Gray
                    else -> "Estado ${doc.estadoCfe ?: "N/A"}" to Color.Gray
                }

                Text(
                    text = statusLabel,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        DetailSection(title = "Información del Receptor") {
            DetailItem(label = "Nombre/Razón Social", value = doc.receptor)
            DetailItem(label = "Estado Receptor", value = doc.estadoReceptor?.toString())
        }

        DetailSection(title = "Totales") {
            DetailItem(label = "Moneda", value = "${doc.monedaCodigo} (${doc.monedaSimbolo})")
            DetailItem(label = "Importe Total", value = "${doc.monedaSimbolo ?: ""} ${doc.importeTotal ?: 0.0}")
            DetailItem(label = "IVA", value = "${doc.monedaSimbolo ?: ""} ${doc.iva ?: 0.0}")
        }

        DetailSection(title = "Fechas") {
            DetailItem(label = "Emisión", value = doc.fechaEmision)
            DetailItem(label = "Confirmación", value = doc.fechaConfirmacion)
            DetailItem(label = "Aceptado UTC", value = doc.fechaAceptadoUtc)
        }

        if (!doc.ultimoError.isNullOrBlank()) {
            DetailSection(title = "Error", titleColor = MaterialTheme.colorScheme.error) {
                Text(
                    text = doc.ultimoError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun DetailSection(
    title: String,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = titleColor,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        content()
    }
}

@Composable
fun DetailItem(label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(text = value ?: "N/A", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

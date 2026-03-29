package com.authvex.balaxysefactura.ui.screens.cfe.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.authvex.balaxysefactura.core.network.CfeSummaryDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CfeListScreen(
    viewModel: CfeListViewModel,
    onBack: () -> Unit,
    onNavigateToDetail: (Int) -> Unit
) {
    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Listado CFE") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDocuments() }) {
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
                is CfeListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is CfeListUiState.Empty -> {
                    Text(
                        text = "No se encontraron documentos.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is CfeListUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = uiState.error.getDisplayMessage(), color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.loadDocuments() }) {
                            Text("Reintentar")
                        }
                    }
                }
                is CfeListUiState.Success -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.documents) { doc ->
                            CfeItem(doc = doc, onClick = { onNavigateToDetail(doc.documentoId) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CfeItem(doc: CfeSummaryDto, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = "${doc.serie ?: ""} ${doc.numero ?: ""}",
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = {
            Column {
                Text(text = doc.receptor ?: "Sin receptor")
                Text(text = "Fecha: ${doc.fechaEmision?.take(10) ?: "N/A"}")
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${doc.monedaSimbolo ?: ""} ${doc.importeTotal ?: 0.0}",
                    fontWeight = FontWeight.Bold
                )
                
                val (label, color) = when (doc.estadoCfe) {
                    6 -> "Aceptado" to Color(0xFF4CAF50)
                    1 -> "Pendiente" to Color.Gray
                    else -> "Estado ${doc.estadoCfe ?: "N/A"}" to Color.Gray
                }

                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
        }
    )
}

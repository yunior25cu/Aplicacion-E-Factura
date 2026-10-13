package com.authvex.balaxysefactura.ui.screens.client

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.authvex.balaxysefactura.core.network.ClientDocumentType
import com.authvex.balaxysefactura.ui.screens.common.DropdownSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientCreateScreen(
    viewModel: ClientCreateViewModel,
    onNavigateBack: () -> Unit,
    onClientCreated: (ClientDocumentType, String) -> Unit
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState

    LaunchedEffect(uiState) {
        if (uiState is ClientCreateUiState.Success) {
            Toast.makeText(context, "Cliente creado correctamente", Toast.LENGTH_SHORT).show()
            onClientCreated(uiState.docType, uiState.documentNumber)
            viewModel.resetState()
        } else if (uiState is ClientCreateUiState.Error) {
            Toast.makeText(context, uiState.message, Toast.LENGTH_LONG).show()
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo Cliente") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Identificación y DGI
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Identificación del Cliente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    DropdownSelector(
                        label = "Tipo de Documento *",
                        selectedOption = viewModel.docType.label,
                        options = ClientDocumentType.entries.map { it.label },
                        onOptionSelected = { index ->
                            viewModel.onDocTypeChanged(ClientDocumentType.entries[index])
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = viewModel.documentNumber,
                            onValueChange = { viewModel.onDocumentNumberChanged(it) },
                            label = { Text("Número de Documento *") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (viewModel.docType == ClientDocumentType.RUC || viewModel.docType == ClientDocumentType.CI)
                                    KeyboardType.Number else KeyboardType.Text
                            )
                        )

                        if (viewModel.docType == ClientDocumentType.RUC) {
                            Button(
                                onClick = { viewModel.queryDgi() },
                                enabled = viewModel.isDgiQueryAvailable() && !viewModel.isQueryingDgi,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(56.dp)
                            ) {
                                if (viewModel.isQueryingDgi) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = "DGI")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("DGI")
                                }
                            }
                        }
                    }

                    if (!viewModel.dgiMessage.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(viewModel.dgiMessage!!, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = viewModel.denominacion,
                        onValueChange = { viewModel.denominacion = it },
                        label = { Text("Nombre / Razón Social *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Card 2: Contacto y Dirección
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Dirección y Contacto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = viewModel.direccion,
                        onValueChange = { viewModel.direccion = it },
                        label = { Text("Dirección") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = viewModel.ciudad,
                            onValueChange = { viewModel.ciudad = it },
                            label = { Text("Ciudad") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = viewModel.departamento,
                            onValueChange = { viewModel.departamento = it },
                            label = { Text("Departamento") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = viewModel.email,
                            onValueChange = { viewModel.email = it },
                            label = { Text("Correo Electrónico") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                        OutlinedTextField(
                            value = viewModel.telefono,
                            onValueChange = { viewModel.telefono = it },
                            label = { Text("Teléfono") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                    }
                }
            }

            // Save Action Button
            Button(
                onClick = { viewModel.saveClient() },
                enabled = uiState !is ClientCreateUiState.Saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState is ClientCreateUiState.Saving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("GUARDAR CLIENTE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

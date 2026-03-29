package com.authvex.balaxysefactura.ui.screens.devtools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsScreen(
    viewModel: DevToolsViewModel,
    onBack: () -> Unit
) {
    val session by viewModel.session.collectAsState()
    val uiState = viewModel.uiState
    var showConfirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Internal DevTools (Debug Only)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Entorno
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Entorno", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("BASE_URL: ${viewModel.baseUrl}")
                    Text("Debug Build: ${viewModel.isDebug}")
                }
            }

            // 2. Sesión actual
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sesión Actual", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    val token = session?.accessToken
                    Text("Token (recortado): ${token?.take(10)}...${token?.takeLast(10)}")
                    Text("Empresa ID: ${session?.empresaId ?: "N/A"}")
                    Text("Usuario ID: ${session?.usuarioId ?: "N/A"}")
                    Text("Expiración: ${session?.expiresAt ?: "N/A"}")
                }
            }

            // 3. Acciones
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Acciones Técnicas", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Button(
                        onClick = { viewModel.onForceRefresh() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState !is DevToolsUiState.Loading
                    ) {
                        Text("Forzar Refresh Token")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showConfirmClear = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Limpiar Sesión Local")
                    }
                }
            }

            // 4. Estado de la prueba
            if (uiState is DevToolsUiState.Success) {
                Text(uiState.message, color = MaterialTheme.colorScheme.primary)
            }
            if (uiState is DevToolsUiState.Error) {
                Text(uiState.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showConfirmClear) {
        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            title = { Text("¿Limpiar sesión?") },
            text = { Text("Esto borrará los tokens locales. Tendrás que volver a loguearte.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onClearSession()
                    showConfirmClear = false
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear = false }) { Text("Cancelar") }
            }
        )
    }
}

package com.authvex.balaxysefactura.ui.screens.product

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.authvex.balaxysefactura.core.network.ProductoDto
import com.authvex.balaxysefactura.ui.screens.common.DropdownSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductCreateScreen(
    viewModel: ProductCreateViewModel,
    onNavigateBack: () -> Unit,
    onProductCreated: (ProductoDto) -> Unit
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState

    LaunchedEffect(uiState) {
        if (uiState is ProductCreateUiState.Success) {
            Toast.makeText(context, "Producto creado correctamente", Toast.LENGTH_SHORT).show()
            onProductCreated(uiState.createdProduct)
            viewModel.resetState()
        } else if (uiState is ProductCreateUiState.Error) {
            Toast.makeText(context, uiState.message, Toast.LENGTH_LONG).show()
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo Producto o Servicio") },
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
            // Card 1: Tipo de Ítem (Producto / Servicio)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tipo de Ítem", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TypeSelectorCard(
                            title = "Producto",
                            icon = Icons.Default.ShoppingBag,
                            isSelected = !viewModel.isService,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.isService = false }
                        )
                        TypeSelectorCard(
                            title = "Servicio",
                            icon = Icons.Default.Build,
                            isSelected = viewModel.isService,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.isService = true }
                        )
                    }
                }
            }

            // Card 2: Información Básica
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Información Básica", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = viewModel.denominacion,
                        onValueChange = { viewModel.denominacion = it },
                        label = { Text("Nombre del Ítem *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = viewModel.codigo,
                        onValueChange = { viewModel.codigo = it },
                        label = { Text("SKU / Código *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DropdownSelector(
                            label = "Unidad de Medida *",
                            selectedOption = viewModel.selectedUnidadMedida?.let { "${it.nombre} (${it.codigo ?: ""})" } ?: "Unidad *",
                            options = viewModel.unidadesList.map { "${it.nombre} (${it.codigo ?: ""})" },
                            onOptionSelected = { index -> viewModel.selectedUnidadMedida = viewModel.unidadesList[index] },
                            modifier = Modifier.weight(1f)
                        )

                        DropdownSelector(
                            label = "Tipo de Producto *",
                            selectedOption = viewModel.selectedTipoProducto?.nombre ?: "Tipo *",
                            options = viewModel.tiposProductoList.map { it.nombre },
                            onOptionSelected = { index -> viewModel.selectedTipoProducto = viewModel.tiposProductoList[index] },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!viewModel.isService) {
                        DropdownSelector(
                            label = "Almacén *",
                            selectedOption = viewModel.selectedAlmacen?.nombre ?: "Almacén *",
                            options = viewModel.almacenesList.map { it.nombre },
                            onOptionSelected = { index -> viewModel.selectedAlmacen = viewModel.almacenesList[index] },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Card 3: Precio e Impuesto
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Precio de Venta e Impuesto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = viewModel.precioVentaText,
                        onValueChange = { viewModel.precioVentaText = it },
                        label = { Text("Precio de Venta *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("El precio incluye IVA", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = viewModel.precioIncluyeIva,
                            onCheckedChange = { viewModel.precioIncluyeIva = it }
                        )
                    }

                    DropdownSelector(
                        label = "Impuesto de Venta",
                        selectedOption = viewModel.selectedImpuestoVenta?.getTaxLabel() ?: "Sin impuesto asignado",
                        options = listOf("Sin impuesto asignado") + viewModel.impuestosVentaList.map { it.getTaxLabel() },
                        onOptionSelected = { index ->
                            viewModel.selectedImpuestoVenta = if (index == 0) null else viewModel.impuestosVentaList[index - 1]
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Card 4: Descripción opcional
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Descripción Técnica", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = viewModel.descripcionTecnica,
                        onValueChange = { viewModel.descripcionTecnica = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Detalles técnicos del ítem...") }
                    )
                }
            }

            // Account warning notice if not set
            if (viewModel.idInventarioTipoCuenta == null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Atención: No hay una cuenta de inventario configurada en la empresa. La creación de productos estará deshabilitada hasta configurarla.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Save Action Button
            Button(
                onClick = { viewModel.saveProduct() },
                enabled = uiState !is ProductCreateUiState.Saving && uiState !is ProductCreateUiState.LoadingCatalogs && viewModel.idInventarioTipoCuenta != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState is ProductCreateUiState.Saving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("GUARDAR Y SELECCIONAR", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun TypeSelectorCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.White
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .height(52.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

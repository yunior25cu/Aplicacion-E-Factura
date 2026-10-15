package com.authvex.balaxysefactura.ui.screens.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.authvex.balaxysefactura.core.network.CfeFiscalIndicadorFacturacionDto
import com.authvex.balaxysefactura.core.network.ClienteDto
import com.authvex.balaxysefactura.core.network.ProductoDto
import java.util.Locale

@Composable
fun SharedClientSelectDialog(
    clientes: List<ClienteDto>,
    isSearching: Boolean = false,
    onSearch: (String) -> Unit,
    onSelect: (ClienteDto) -> Unit,
    onDismiss: () -> Unit,
    onCreateNewClient: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Seleccionar Cliente", style = MaterialTheme.typography.titleLarge)
                    if (onCreateNewClient != null) {
                        TextButton(onClick = onCreateNewClient) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nuevo", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                var query by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onSearch(it)
                    },
                    label = { Text("Buscar por nombre, RUT, etc.") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = ""; onSearch("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                if (!isSearching && clientes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (query.isEmpty()) "No hay clientes registrados" else "No se encontraron clientes para '$query'",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        itemsIndexed(clientes) { _, client ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(client) }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(client.nombre, fontWeight = FontWeight.SemiBold)
                                    Text(client.documentNumber ?: "Sin RUT/CI", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SharedProductSelectDialog(
    products: List<ProductoDto>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onSelect: (ProductoDto) -> Unit,
    onDismiss: () -> Unit,
    onCreateNewProduct: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Seleccionar Producto", style = MaterialTheme.typography.titleLarge)
                    if (onCreateNewProduct != null) {
                        TextButton(onClick = onCreateNewProduct) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nuevo", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                var query by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onSearch(it)
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = ""; onSearch("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar por nombre, SKU o código...") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                if (!isSearching && products.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (query.isEmpty()) "No hay productos disponibles" else "No se encontraron productos para '$query'",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        itemsIndexed(products) { _, p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(p) }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.nombre, fontWeight = FontWeight.SemiBold)
                                    Text("Código: ${p.codigo ?: "-"} | Precio: $ ${p.precio ?: 0.0}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SharedLineConfiguratorDialog(
    productName: String,
    initialQuantity: String,
    initialPrice: String,
    currencySymbol: String,
    indicadoresC4: List<CfeFiscalIndicadorFacturacionDto> = emptyList(),
    initialC4: Int? = null,
    isResolvingC4: Boolean = false,
    stockLoading: Boolean = false,
    stockExistence: Double? = null,
    stockError: Boolean = false,
    errorMessage: String? = null,
    onConfirm: (quantity: Double, price: Double, indicadorC4: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var qty by remember(initialQuantity) { mutableStateOf(initialQuantity) }
    var price by remember(initialPrice) { mutableStateOf(initialPrice) }
    var selectedC4 by remember(initialC4) { mutableStateOf(initialC4) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(productName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = { Text("Cantidad") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (stockLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Consultando existencia...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (stockExistence != null) {
                    val formattedStock = if (stockExistence % 1.0 == 0.0) String.format(Locale.US, "%.0f", stockExistence) else String.format(Locale.US, "%.2f", stockExistence)
                    val stockColor = if (stockExistence <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        text = "Existencia en almacén: $formattedStock",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = stockColor
                    )
                } else if (stockError) {
                    Text(
                        text = "Existencia no disponible",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Precio Unitario ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val qtyNum = qty.replace(',', '.').toDoubleOrNull() ?: 0.0
                val priceNum = price.replace(',', '.').toDoubleOrNull() ?: 0.0
                val totalPreview = qtyNum * priceNum
                val isOverStock = !stockError && stockExistence != null && qtyNum > stockExistence + 0.00001
                val effectiveError = if (isOverStock) "La cantidad no puede superar la existencia en almacén." else errorMessage

                Text(
                    text = "Subtotal Estimado: $currencySymbol ${String.format(Locale.US, "%.2f", totalPreview)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (isResolvingC4) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }

                if (indicadoresC4.isNotEmpty()) {
                    val selectedC4Obj = indicadoresC4.find { it.id == selectedC4 }
                    DropdownSelector(
                        label = "Indicador Facturación (C4)",
                        selectedOption = selectedC4Obj?.name ?: "Indicador C4",
                        options = indicadoresC4.map { it.name },
                        onOptionSelected = { index -> selectedC4 = indicadoresC4[index].id }
                    )
                }

                if (!effectiveError.isNullOrBlank()) {
                    Text(
                        text = effectiveError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(qtyNum, priceNum, selectedC4)
                        },
                        enabled = !stockLoading && !isOverStock
                    ) {
                        Text("Confirmar")
                    }
                }
            }
        }
    }
}

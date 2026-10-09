package com.authvex.balaxysefactura.ui.screens.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.authvex.balaxysefactura.core.network.ProductoDto
import java.util.Locale

@Composable
fun SharedProductSelectDialog(
    products: List<ProductoDto>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onSelect: (ProductoDto) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Seleccionar Producto", style = MaterialTheme.typography.titleLarge)
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

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
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
                    Button(onClick = {
                        onConfirm(qty.replace(',', '.').toDoubleOrNull() ?: 0.0, price.replace(',', '.').toDoubleOrNull() ?: 0.0, selectedC4)
                    }) {
                        Text("Confirmar")
                    }
                }
            }
        }
    }
}

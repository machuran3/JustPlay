package com.justplay.pos.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.justplay.pos.core.models.Product
import com.justplay.pos.ui.theme.*

@Composable
fun ProductManagerDialog(
    initialProduct: Product?,
    prefillBarcode: String = "",
    existingProducts: List<Product> = emptyList(),
    onSaveProduct: (Product, String?) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var barcode by remember { mutableStateOf(initialProduct?.barcode ?: prefillBarcode) }
    var name by remember { mutableStateOf(initialProduct?.name ?: "") }
    var gramaje by remember { mutableStateOf(initialProduct?.gramaje ?: "") }
    var category by remember { mutableStateOf(initialProduct?.category ?: "Snacks") }
    var price by remember { mutableStateOf(initialProduct?.unitPrice?.toString() ?: "10.0") }
    var costPrice by remember { mutableStateOf(initialProduct?.costPrice?.toString() ?: "5.0") }
    var stockInput by remember { mutableStateOf(initialProduct?.currentStock?.toString() ?: "50") }
    var minStock by remember { mutableStateOf(initialProduct?.minStockAlert?.toString() ?: "5") }
    var unitOfMeasure by remember { mutableStateOf(initialProduct?.unitOfMeasure ?: "Unidad") }

    val cleanBarcode = barcode.trim()
    val cleanName = name.trim()

    val baseStock = initialProduct?.currentStock ?: 0
    val calculatedStock = remember(stockInput, baseStock) {
        val s = stockInput.trim()
        when {
            s.startsWith("+") -> {
                val delta = s.removePrefix("+").trim().toIntOrNull() ?: 0
                baseStock + delta
            }
            s.contains("+") -> {
                s.split("+").sumOf { it.trim().toIntOrNull() ?: 0 }
            }
            else -> s.toIntOrNull() ?: baseStock
        }
    }

    val isDuplicateBarcode = cleanBarcode.isNotBlank() && existingProducts.any {
        it.barcode.trim().equals(cleanBarcode, ignoreCase = true) &&
                (initialProduct == null || !cleanBarcode.equals(initialProduct.barcode.trim(), ignoreCase = true))
    }

    val isDuplicateName = cleanName.isNotBlank() && existingProducts.any {
        it.barcode.trim() != (initialProduct?.barcode?.trim() ?: "") &&
                it.name.trim().equals(cleanName, ignoreCase = true)
    }

    val canSave = cleanBarcode.isNotBlank() && cleanName.isNotBlank() && !isDuplicateBarcode && !isDuplicateName

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            modifier = Modifier.width(480.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (initialProduct == null) "📦 Nuevo Producto al Catálogo" else "✏️ Editar Producto",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Código de Barras (Editable)
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Código de Barras", fontSize = 12.sp) },
                    isError = isDuplicateBarcode,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedBorderColor = if (isDuplicateBarcode) AlertRed else AccentLime,
                        unfocusedBorderColor = if (isDuplicateBarcode) AlertRed else BorderDark,
                        errorBorderColor = AlertRed,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isDuplicateBarcode) {
                    Text(
                        text = "❌ Ya existe otro producto con este código de barras.",
                        color = AlertRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Nombre y Gramaje/Volumen separados
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre del Producto", fontSize = 12.sp) },
                        isError = isDuplicateName,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedBorderColor = if (isDuplicateName) AlertRed else AccentLime,
                            unfocusedBorderColor = if (isDuplicateName) AlertRed else BorderDark,
                            errorBorderColor = AlertRed,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.3f)
                    )

                    OutlinedTextField(
                        value = gramaje,
                        onValueChange = { gramaje = it },
                        label = { Text("Gramaje/Volumen", fontSize = 12.sp) },
                        placeholder = { Text("500ml, 1L", fontSize = 11.sp, color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedBorderColor = AccentLime,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (isDuplicateName) {
                    Text(
                        text = "❌ Ya existe otro producto registrado con este nombre.",
                        color = AlertRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Categoría fija (Dropdown / Selector)
                Text("Categoría:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Snack", "Bebidas", "Golosinas").forEach { cat ->
                        FilterChip(
                            selected = category.equals(cat, ignoreCase = true) || (cat == "Snack" && category.startsWith("Snack", ignoreCase = true)),
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SurfaceDark,
                                labelColor = TextSecondary,
                                selectedContainerColor = AccentLime,
                                selectedLabelColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Precios
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Precio Venta (Bs)", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedBorderColor = AccentLime,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = costPrice,
                        onValueChange = { costPrice = it },
                        label = { Text("Costo Compra (Bs)", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedBorderColor = AccentLime,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Stock con soporte para Aumento Rápido (+X)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = stockInput,
                            onValueChange = { stockInput = it },
                            label = { Text("Stock Actual (o +10)", fontSize = 11.sp) },
                            placeholder = { Text("Ej: 40 o +10", fontSize = 11.sp, color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceDark,
                                unfocusedContainerColor = SurfaceDark,
                                focusedBorderColor = AccentLime,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (stockInput.trim().startsWith("+") || stockInput.trim().contains("+")) {
                            Text(
                                text = "⚡ Stock resultante: $calculatedStock u.",
                                color = AccentLime,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = minStock,
                        onValueChange = { minStock = it },
                        label = { Text("Alerta Stock Mínimo", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedBorderColor = AccentLime,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Botones rápidos para sumar stock
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Suma rápida:", color = TextMuted, fontSize = 10.sp)
                    listOf(5, 10, 20, 50).forEach { delta ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                        ) {
                            TextButton(
                                onClick = {
                                    val currentNum = stockInput.removePrefix("+").toIntOrNull() ?: baseStock
                                    stockInput = (currentNum + delta).toString()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("+$delta", color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Botones Guardar / Eliminar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (initialProduct != null) {
                        Button(
                            onClick = { onDeleteProduct(initialProduct.barcode) },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Eliminar", fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Button(
                        onClick = {
                            if (canSave) {
                                onSaveProduct(
                                    Product(
                                        barcode = cleanBarcode,
                                        name = cleanName,
                                        gramaje = gramaje.trim(),
                                        category = category,
                                        unitPrice = price.toDoubleOrNull() ?: 10.0,
                                        costPrice = costPrice.toDoubleOrNull() ?: 5.0,
                                        currentStock = calculatedStock,
                                        minStockAlert = minStock.toIntOrNull() ?: 5,
                                        unitOfMeasure = unitOfMeasure
                                    ),
                                    initialProduct?.barcode
                                )
                            }
                        },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentLime,
                            contentColor = Color.Black,
                            disabledContainerColor = SurfaceDark,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar Producto", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

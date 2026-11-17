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
import androidx.compose.ui.input.key.*
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
    
    val initialGramajeRaw = initialProduct?.gramaje?.trim() ?: ""
    val unitRegex = remember { Regex("""(?i)^([\d.,]+)\s*([a-zA-ZáéíóúÁÉÍÓÚ]+)?$""") }
    val initialMatch = remember { unitRegex.find(initialGramajeRaw) }
    var gramajeQty by remember { mutableStateOf(initialMatch?.groupValues?.getOrNull(1) ?: initialGramajeRaw) }
    var selectedUnit by remember {
        val parsedUnit = initialMatch?.groupValues?.getOrNull(2)?.lowercase() ?: ""
        mutableStateOf(
            when {
                parsedUnit.startsWith("ml") -> "ml"
                parsedUnit.startsWith("l") -> "L"
                parsedUnit.startsWith("kg") || parsedUnit.startsWith("kilo") -> "kg"
                parsedUnit.startsWith("g") || parsedUnit.startsWith("gr") -> "g"
                parsedUnit.startsWith("u") || parsedUnit.startsWith("pza") -> "u"
                initialGramajeRaw.isBlank() -> "ml"
                else -> "Otro"
            }
        )
    }
    var customUnitText by remember {
        mutableStateOf(if (selectedUnit == "Otro") (initialMatch?.groupValues?.getOrNull(2) ?: "") else "")
    }

    val effectiveGramaje = remember(gramajeQty, selectedUnit, customUnitText) {
        val q = gramajeQty.trim()
        if (q.isBlank()) ""
        else {
            val u = if (selectedUnit == "Otro") customUnitText.trim() else selectedUnit
            if (u.isBlank()) q else "$q $u"
        }
    }

    var isSaving by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(initialProduct?.category ?: "Snacks") }
    var price by remember { mutableStateOf(initialProduct?.unitPrice?.toString() ?: "10.0") }
    var costPrice by remember { mutableStateOf(initialProduct?.costPrice?.toString() ?: "5.0") }
    var stockInput by remember { mutableStateOf(initialProduct?.currentStock?.toString() ?: "50") }
    var minStock by remember { mutableStateOf(initialProduct?.minStockAlert?.toString() ?: "5") }
    var unitOfMeasure by remember { mutableStateOf(initialProduct?.unitOfMeasure ?: "Unidad") }

    val cleanBarcode = barcode.trim()
    val cleanName = name.trim()

    val parsedPrice = price.toDoubleOrNull() ?: 0.0
    val parsedCost = costPrice.toDoubleOrNull() ?: 0.0
    // Validación: El costo de compra no puede ser mayor al precio de venta
    val isCostInvalid = price.isNotBlank() && costPrice.isNotBlank() && (parsedCost > parsedPrice)

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

    fun normalizeGramaje(g: String) = g.trim().replace("\\s+".toRegex(), "").lowercase()

    val isDuplicateNameAndGramaje = cleanName.isNotBlank() && existingProducts.any {
        it.barcode.trim() != (initialProduct?.barcode?.trim() ?: "") &&
                it.name.trim().equals(cleanName, ignoreCase = true) &&
                normalizeGramaje(it.gramaje) == normalizeGramaje(effectiveGramaje)
    }

    val hasSameNameDifferentGramaje = cleanName.isNotBlank() && !isDuplicateNameAndGramaje && existingProducts.any {
        it.barcode.trim() != (initialProduct?.barcode?.trim() ?: "") &&
                it.name.trim().equals(cleanName, ignoreCase = true)
    }

    val canSave = cleanBarcode.isNotBlank() && cleanName.isNotBlank() && !isDuplicateBarcode && !isDuplicateNameAndGramaje && !isCostInvalid && parsedPrice > 0.0

    val performSave: () -> Unit = {
        if (canSave && !isSaving) {
            isSaving = true
            onSaveProduct(
                Product(
                    barcode = cleanBarcode,
                    name = cleanName,
                    gramaje = effectiveGramaje.trim(),
                    category = category,
                    unitPrice = parsedPrice,
                    costPrice = parsedCost,
                    currentStock = calculatedStock,
                    minStockAlert = minStock.toIntOrNull() ?: 5,
                    unitOfMeasure = if (selectedUnit == "Otro") customUnitText.ifBlank { "Unidad" } else selectedUnit
                ),
                initialProduct?.barcode
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .width(480.dp)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown) {
                        if (canSave && !isSaving) {
                            performSave()
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                }
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

                // Nombre del producto
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Producto", fontSize = 12.sp) },
                    isError = isDuplicateNameAndGramaje,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedBorderColor = if (isDuplicateNameAndGramaje) AlertRed else AccentLime,
                        unfocusedBorderColor = if (isDuplicateNameAndGramaje) AlertRed else BorderDark,
                        errorBorderColor = AlertRed,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isDuplicateNameAndGramaje) {
                    val duplicateMsg = if (effectiveGramaje.isNotBlank()) {
                        "❌ Ya existe otro producto registrado con el mismo nombre y gramaje/volumen ($cleanName $effectiveGramaje)."
                    } else {
                        "❌ Ya existe un producto con el nombre '$cleanName'. Ingresa el gramaje/volumen para diferenciarlo (ej: 500 ml, 2 L)."
                    }
                    Text(
                        text = duplicateMsg,
                        color = AlertRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                } else if (hasSameNameDifferentGramaje) {
                    Text(
                        text = "ℹ️ Se registrará como variante de presentación de '$cleanName' (${effectiveGramaje.ifBlank { "otra presentación" }}).",
                        color = AccentLime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Selección Dinámica de Gramaje / Volumen y Unidad de Medida
                Text("Gramaje / Volumen & Unidad de Medida:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = gramajeQty,
                        onValueChange = { gramajeQty = it },
                        label = { Text("Cantidad / Gramaje", fontSize = 11.sp) },
                        placeholder = { Text("500, 1.5, 250", fontSize = 11.sp, color = TextMuted) },
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
                        modifier = Modifier.weight(1.2f)
                    )

                    if (selectedUnit == "Otro") {
                        OutlinedTextField(
                            value = customUnitText,
                            onValueChange = { customUnitText = it },
                            label = { Text("Unidad", fontSize = 11.sp) },
                            placeholder = { Text("pack, barra", fontSize = 11.sp, color = TextMuted) },
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
                            modifier = Modifier.weight(0.9f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Chips para selector de unidades de medida
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("ml", "L", "g", "kg", "u", "Otro").forEach { unitOpt ->
                        FilterChip(
                            selected = selectedUnit == unitOpt,
                            onClick = { selectedUnit = unitOpt },
                            label = { Text(unitOpt, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SurfaceDark,
                                labelColor = TextSecondary,
                                selectedContainerColor = AccentLime,
                                selectedLabelColor = Color.Black
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (effectiveGramaje.isNotBlank()) {
                    Text(
                        text = "🏷️ Presentación resultante: $effectiveGramaje",
                        color = AccentLime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp)
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
                        isError = isCostInvalid,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedBorderColor = if (isCostInvalid) AlertRed else AccentLime,
                            unfocusedBorderColor = if (isCostInvalid) AlertRed else BorderDark,
                            errorBorderColor = AlertRed,
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
                        isError = isCostInvalid,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedBorderColor = if (isCostInvalid) AlertRed else AccentLime,
                            unfocusedBorderColor = if (isCostInvalid) AlertRed else BorderDark,
                            errorBorderColor = AlertRed,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (isCostInvalid) {
                    Text(
                        text = "❌ El costo de compra ($parsedCost Bs) no puede ser mayor al precio de venta ($parsedPrice Bs).",
                        color = AlertRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
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
                        onClick = performSave,
                        enabled = canSave && !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentLime,
                            contentColor = Color.Black,
                            disabledContainerColor = SurfaceDark,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardando...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardar Producto", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

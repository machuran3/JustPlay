package com.justplay.pos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.justplay.pos.core.models.Product
import com.justplay.pos.core.models.Reservation
import com.justplay.pos.core.models.Room
import com.justplay.pos.core.models.RoomOrder
import com.justplay.pos.core.models.RoomState
import com.justplay.pos.core.utils.ReceiptImageUtils
import com.justplay.pos.ui.theme.*

@Composable
fun RoomDetailDialog(
    room: Room,
    prefillReservation: Reservation? = null,
    pendingOrders: List<RoomOrder>,
    catalogProducts: List<Product>,
    lastScannedBarcode: String = "",
    isWaitingQrValidation: Boolean = false,
    qrValidationStatusMessage: String? = null,
    verifiedReceiptUrl: String? = null,
    onRequestQrValidation: (client: String, phone: String, gameOrMovie: String, durationMin: Int, price: Double, method: String, efec: Double, qr: Double, pedidos: List<RoomOrder>) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onCancelQrValidation: () -> Unit = {},
    onPickReceiptPhotos: (() -> Unit)? = null,
    onUpdateReceiptPhotos: ((String?) -> Unit)? = null,
    onPickActiveRoomPhotos: ((roomId: Int) -> Unit)? = null,
    onUpdateActiveRoomPhotos: ((roomId: Int, newReceiptString: String?) -> Unit)? = null,
    onAddProduct: (Product) -> Unit,
    onRemoveProduct: (Int) -> Unit,
    onAddTime: (extraMinutes: Int) -> Unit,
    onClearRoom: () -> Unit,
    onConfirmStart: (client: String, phone: String, gameOrMovie: String, durationMin: Int, price: Double, paymentMethod: String, pedidos: List<RoomOrder>, receiptImageUrl: String?) -> Unit,
    onUpdateActiveRoomSession: ((client: String, phone: String, gameOrMovie: String, price: Double, extraMinutes: Int?, paymentMethod: String) -> Unit)? = null,
    isActionInProgress: Boolean = false,
    onDismiss: () -> Unit
) {
    val isOccupied = room.estado == RoomState.OCUPADA
    var showReceiptViewerForStarting by remember { mutableStateOf(false) }
    var showReceiptViewerForOccupied by remember { mutableStateOf(false) }

    var clientName by remember(room, prefillReservation) {
        mutableStateOf(if (isOccupied) (room.cliente ?: "") else (prefillReservation?.nombre ?: (room.cliente ?: "")))
    }

    var clientPhone by remember(room, prefillReservation) {
        mutableStateOf(if (isOccupied) (room.celular ?: "") else (prefillReservation?.celular ?: (room.celular ?: "")))
    }
    var gameOrMovie by remember(room, prefillReservation) {
        mutableStateOf(room.pelicula ?: "")
    }
    var manualTimeText by remember(room, prefillReservation) {
        mutableStateOf(if (!isOccupied && prefillReservation != null && prefillReservation.duracion.isNotBlank()) prefillReservation.duracion else "")
    }
    var selectedDurationPreset by remember(room, prefillReservation) {
        mutableStateOf<Int?>(if (!isOccupied && prefillReservation != null) prefillReservation.parseDurationMinutes() else null)
    }
    // Precio base sugerido: Minicine = 35 Bs, Volantes/Just Dance = 15 Bs
    var priceText by remember(room, prefillReservation) {
        mutableStateOf(if (isOccupied) room.precioCobrado.toString() else room.precioBaseSugerido.toString())
    }
    var selectedPaymentMethod by remember(room, prefillReservation) {
        mutableStateOf(
            if (isOccupied) room.metodoPago.ifBlank { "Efectivo" }
            else (prefillReservation?.metodoPagoAdelanto?.ifBlank { "Efectivo" } ?: room.metodoPago.ifBlank { "Efectivo" })
        )
    }

    // Estado para modo edición de sala ocupada
    var isEditModeActive by remember { mutableStateOf(false) }
    var editClientName by remember { mutableStateOf(room.cliente ?: "") }
    var editClientPhone by remember { mutableStateOf(room.celular ?: "") }
    var editGameOrMovie by remember { mutableStateOf(room.pelicula ?: "") }
    var editPriceText by remember { mutableStateOf(room.precioCobrado.toString()) }
    var editPaymentMethod by remember { mutableStateOf(room.metodoPago.ifBlank { "Efectivo" }) }
    var editExtraMinutesText by remember { mutableStateOf("") }
    var editSaveSuccessMessage by remember { mutableStateOf<String?>(null) }
    var cashAmountMixto by remember { 
        mutableStateOf(
            if (isOccupied && room.metodoPago.startsWith("Mixto", ignoreCase = true)) {
                val efec = if (room.montoEfectivo > 0.0) room.montoEfectivo else {
                    val regex = Regex("""(?i)(?:efec|ef|💵)\s*:?\s*([\d.]+)""")
                    val m = regex.find(room.metodoPago)
                    m?.groupValues?.get(1)?.toDoubleOrNull() ?: (room.precioCobrado / 2)
                }
                if (efec > 0.0) efec.toString() else ""
            } else ""
        ) 
    }
    var customExtraTimeText by remember { mutableStateOf("") }
    var showQrVerificationPromptDialog by remember { mutableStateOf(false) }
    var isSubmittingAction by remember { mutableStateOf(false) }
    var showMinicineTarifaDialog by remember { mutableStateOf(false) }

    if (showMinicineTarifaDialog) {
        MinicineTarifaDialog(
            onDismiss = { showMinicineTarifaDialog = false },
            onSelectTarifa = { mins, precio ->
                manualTimeText = when {
                    mins % 60 == 0 -> "${mins / 60}:00"
                    else -> "${mins / 60}:${(mins % 60).toString().padStart(2, '0')}"
                }
                priceText = (if (precio % 1.0 == 0.0) precio.toInt().toString() else precio.toString())
                selectedDurationPreset = mins
                showMinicineTarifaDialog = false
            }
        )
    }

    // Limpieza automática de cualquier dígito o código de barras que se filtre al escanear productos en la sala
    LaunchedEffect(lastScannedBarcode, pendingOrders.size, room.pedidos.size) {
        if (lastScannedBarcode.isNotBlank()) {
            val code = lastScannedBarcode.trim()
            fun sanitizeField(field: String): String {
                var s = field
                if (s.endsWith(code)) {
                    s = s.removeSuffix(code)
                }
                // Si contiene el código completo o dígitos largos al final
                if (code.length >= 4 && s.endsWith(code.takeLast(4))) {
                    s = s.removeSuffix(code.takeLast(4))
                }
                // Si se filtró el primer caracter (o primeros caracteres) del código al principio de la ráfaga
                for (prefixLen in minOf(4, code.length) downTo 1) {
                    val prefix = code.take(prefixLen)
                    if (s.endsWith(prefix) && s.length > prefixLen) {
                        s = s.removeSuffix(prefix)
                        break
                    }
                }
                // Limpiar dígitos sueltos al final si vinieron de una ráfaga
                val trailingDigits = s.takeLastWhile { it.isDigit() }
                if (trailingDigits.length >= 1 && code.startsWith(trailingDigits) && s.length > trailingDigits.length) {
                    s = s.dropLast(trailingDigits.length)
                }
                return s.trimEnd()
            }

            clientName = sanitizeField(clientName)
            clientPhone = if (!clientPhone.all { it.isDigit() }) sanitizeField(clientPhone) else clientPhone
            gameOrMovie = sanitizeField(gameOrMovie)
            manualTimeText = if (manualTimeText.length > 5) sanitizeField(manualTimeText) else manualTimeText
        }
    }

    fun parseDurationMinutes(input: String): Int {
        val clean = input.trim().lowercase()
        if (clean.isEmpty() || clean == "libre" || clean == "0") return 0
        if (clean.contains(":")) {
            val parts = clean.split(":")
            val hours = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
            val minutes = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            return hours * 60 + minutes
        }
        val num = clean.filter { it.isDigit() }.toIntOrNull() ?: 60
        return num
    }

    // Buscador y escáner de código de barras dentro del diálogo
    var barcodeSearchInput by remember { mutableStateOf("") }
    val filteredCatalog = remember(catalogProducts, barcodeSearchInput) {
        val q = barcodeSearchInput.trim().lowercase()
        if (q.isEmpty()) {
            catalogProducts.take(8)
        } else {
            catalogProducts.filter {
                it.name.lowercase().contains(q) || it.barcode.contains(q) || it.category.lowercase().contains(q)
            }.take(8)
        }
    }

    val currentOrders = if (isOccupied) room.pedidos else pendingOrders
    val basePrice = if (isOccupied) room.precioCobrado else (priceText.toDoubleOrNull() ?: room.precioBaseSugerido)
    val totalConsumos = currentOrders.sumOf { it.subtotal }
    val grandTotal = basePrice + totalConsumos

    val performConfirmStart: () -> Unit = {
        if (!isOccupied && !isActionInProgress && !isSubmittingAction && clientName.isNotBlank()) {
            val price = priceText.toDoubleOrNull() ?: room.precioBaseSugerido
            val duration = parseDurationMinutes(manualTimeText)
            if ((selectedPaymentMethod == "QR" || selectedPaymentMethod == "Mixto") && verifiedReceiptUrl == null) {
                showQrVerificationPromptDialog = true
            } else {
                isSubmittingAction = true
                onConfirmStart(
                    clientName.trim(),
                    clientPhone.trim(),
                    gameOrMovie.trim(),
                    duration,
                    price,
                    selectedPaymentMethod,
                    pendingOrders,
                    verifiedReceiptUrl
                )
            }
        }
    }

    val performUpdateSession: () -> Unit = {
        if (isOccupied && isEditModeActive && !isActionInProgress && !isSubmittingAction && editClientName.isNotBlank() && onUpdateActiveRoomSession != null) {
            isSubmittingAction = true
            val pr = editPriceText.toDoubleOrNull() ?: room.precioCobrado
            val extraMin = editExtraMinutesText.toIntOrNull()
            onUpdateActiveRoomSession(
                editClientName.trim(),
                editClientPhone.trim(),
                editGameOrMovie.trim(),
                pr,
                extraMin,
                editPaymentMethod
            )
            editSaveSuccessMessage = "✅ Parámetros guardados y sincronizados"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1C29),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .width(980.dp)
                .heightIn(min = 680.dp, max = 760.dp)
                .fillMaxHeight(0.94f)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown) {
                        if (barcodeSearchInput.isNotBlank()) {
                            // Si el foco está en el buscador de snacks de sala, no interrumpe el escaneo/búsqueda de snack
                            return@onPreviewKeyEvent false
                        }
                        if (!isOccupied) {
                            if (clientName.isNotBlank() && !isActionInProgress && !isSubmittingAction) {
                                performConfirmStart()
                                return@onPreviewKeyEvent true
                            }
                        } else if (isEditModeActive) {
                            if (editClientName.isNotBlank() && !isActionInProgress && !isSubmittingAction) {
                                performUpdateSession()
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    false
                }
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                // 1. Cabecera del diálogo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconPrefix = when (room.categoria) {
                        "Minicine" -> "🎬"
                        "Autitos" -> "🏎️"
                        else -> "💃"
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "$iconPrefix ${room.nombre} (${room.categoria})",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isOccupied) AlertRed.copy(alpha = 0.2f) else AccentGreen.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = if (isOccupied) "🔴 OCUPADA" else "🟢 DISPONIBLE",
                                color = if (isOccupied) AlertRed else AccentGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (prefillReservation != null) {
                    Surface(
                        color = Color(0xFF1E3A2B),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentLime),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("📋", fontSize = 22.sp)
                            Column {
                                Text(
                                    "Traspasando Reserva: ${prefillReservation.nombre}",
                                    color = AccentLime,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                val infoAdelanto = if (prefillReservation.adelanto.isNotBlank()) " | 💰 Adelanto: ${prefillReservation.adelanto} Bs (${prefillReservation.metodoPagoAdelanto})" else ""
                                Text(
                                    "Hora Programada: ${prefillReservation.horaProgramada} | Duración: ${prefillReservation.duracion}$infoAdelanto",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // 2. Contenido Principal (2 Columnas amplias)
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // COLUMNA IZQUIERDA: Sesión / Formulario
                    val leftScrollState = rememberScrollState()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.weight(1.1f).fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(leftScrollState)
                                .padding(16.dp)
                        ) {
                            if (!isOccupied) {
                                Text("📋 Datos de la Función", color = AccentLime, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = clientName,
                                    onValueChange = { clientName = it },
                                    label = { Text("Nombre del Cliente *", fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = CardDark,
                                        unfocusedContainerColor = CardDark,
                                        focusedBorderColor = AccentLime,
                                        unfocusedBorderColor = BorderDark,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                if (room.categoria == "Minicine") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = clientPhone,
                                            onValueChange = { clientPhone = it },
                                            label = { Text("Celular (Opcional)", fontSize = 12.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = CardDark,
                                                unfocusedContainerColor = CardDark,
                                                focusedBorderColor = AccentLime,
                                                unfocusedBorderColor = BorderDark,
                                                focusedTextColor = TextPrimary,
                                                unfocusedTextColor = TextPrimary
                                            )
                                        )

                                        OutlinedTextField(
                                            value = gameOrMovie,
                                            onValueChange = { gameOrMovie = it },
                                            label = { Text("Película / Contenido", fontSize = 12.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = CardDark,
                                                unfocusedContainerColor = CardDark,
                                                focusedBorderColor = AccentLime,
                                                unfocusedBorderColor = BorderDark,
                                                focusedTextColor = TextPrimary,
                                                unfocusedTextColor = TextPrimary
                                            )
                                        )
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = clientPhone,
                                        onValueChange = { clientPhone = it },
                                        label = { Text("Celular (Opcional)", fontSize = 12.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = CardDark,
                                            unfocusedContainerColor = CardDark,
                                            focusedBorderColor = AccentLime,
                                            unfocusedBorderColor = BorderDark,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⏱️ Tiempo de Alquiler:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    if (room.categoria == "Minicine" || room.id < 100) {
                                        Surface(
                                            color = AccentLime.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentLime.copy(alpha = 0.5f)),
                                            modifier = Modifier.clickable { showMinicineTarifaDialog = true }
                                        ) {
                                            Text(
                                                text = "⏱️ Ver Tabla de Tarifas",
                                                color = AccentLime,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))

                                // Campo Manual de Tiempo
                                OutlinedTextField(
                                    value = manualTimeText,
                                    onValueChange = {
                                        manualTimeText = it
                                        selectedDurationPreset = null
                                        if (room.categoria == "Minicine" || room.id < 100) {
                                            val mins = com.justplay.pos.core.utils.MinicineTarifaCalculator.parsearTextoAMinutos(it)
                                            if (mins > 0) {
                                                val calc = com.justplay.pos.core.utils.MinicineTarifaCalculator.calcular(mins)
                                                priceText = if (calc % 1.0 == 0.0) calc.toInt().toString() else calc.toString()
                                            }
                                        }
                                    },
                                    label = { Text("Tiempo (Ej: 1:20 o minutos)", fontSize = 11.sp) },
                                    placeholder = { Text("Ej: 1:20", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = CardDark,
                                        unfocusedContainerColor = CardDark,
                                        focusedBorderColor = AccentLime,
                                        unfocusedBorderColor = BorderDark,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                val isMinicine = room.categoria == "Minicine" || room.id < 100

                                // Fila 1 de Atajos de Duración
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val row1 = if (isMinicine) {
                                        listOf(60 to "1 hora", 90 to "1h 30m", 120 to "2h (40 Bs)")
                                    } else {
                                        listOf(30 to "30 min", 60 to "1h (Base)", 90 to "1h 30 min")
                                    }
                                    for ((mins, label) in row1) {
                                        val isSelected = selectedDurationPreset == mins
                                        Button(
                                            onClick = {
                                                selectedDurationPreset = mins
                                                manualTimeText = when(mins) {
                                                    30 -> "0:30"
                                                    60 -> "1:00"
                                                    90 -> "1:30"
                                                    120 -> "2:00"
                                                    else -> "$mins"
                                                }
                                                if (isMinicine) {
                                                    val calc = com.justplay.pos.core.utils.MinicineTarifaCalculator.calcular(mins)
                                                    priceText = if (calc % 1.0 == 0.0) calc.toInt().toString() else calc.toString()
                                                } else {
                                                    if (mins == 60) {
                                                        priceText = room.precioBaseSugerido.toString()
                                                    } else if (mins == 30) {
                                                        priceText = (room.precioBaseSugerido / 2).toString()
                                                    } else if (mins == 90) {
                                                        priceText = (room.precioBaseSugerido * 1.5).toString()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) AccentLime else CardDark,
                                                contentColor = if (isSelected) Color.Black else Color.White
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f).height(34.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Fila 2 de Atajos de Duración
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val row2 = if (isMinicine) {
                                        listOf(180 to "3h (70 Bs)", 240 to "4h (100 Bs)", 0 to "Libre")
                                    } else {
                                        listOf(120 to "2 horas", 0 to "Tiempo Libre")
                                    }
                                    for ((mins, label) in row2) {
                                        val isSelected = selectedDurationPreset == mins
                                        Button(
                                            onClick = {
                                                selectedDurationPreset = mins
                                                manualTimeText = when(mins) {
                                                    120 -> "2:00"
                                                    180 -> "3:00"
                                                    240 -> "4:00"
                                                    0 -> "Libre"
                                                    else -> "$mins"
                                                }
                                                if (isMinicine) {
                                                    val calc = com.justplay.pos.core.utils.MinicineTarifaCalculator.calcular(mins)
                                                    priceText = if (calc % 1.0 == 0.0) calc.toInt().toString() else calc.toString()
                                                } else {
                                                    if (mins == 120) {
                                                        priceText = (room.precioBaseSugerido * 2).toString()
                                                    } else if (mins == 0) {
                                                        priceText = room.precioBaseSugerido.toString()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) AccentLime else CardDark,
                                                contentColor = if (isSelected) Color.Black else Color.White
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f).height(34.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Tarifa y Método de Pago
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = priceText,
                                        onValueChange = { priceText = it },
                                        label = { Text("Tarifa Sala (Bs)", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = CardDark,
                                            unfocusedContainerColor = CardDark,
                                            focusedBorderColor = AccentLime,
                                            unfocusedBorderColor = BorderDark,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        )
                                    )

                                    Column(modifier = Modifier.weight(1.4f)) {
                                        Text("Método de Pago:", color = TextSecondary, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                            val methods = listOf("Efectivo" to "💵 Efec", "QR" to "📱 QR", "Mixto" to "💵📱 Mixto")
                                            for ((key, lbl) in methods) {
                                                Button(
                                                    onClick = { 
                                                        selectedPaymentMethod = key 
                                                        if (key == "Mixto" && cashAmountMixto.isBlank()) {
                                                            val half = grandTotal / 2.0
                                                            cashAmountMixto = if (half > 0.0) {
                                                                if (half % 1.0 == 0.0) half.toInt().toString() else String.format(Locale.US, "%.1f", half)
                                                            } else ""
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (selectedPaymentMethod == key) AccentLime else CardDark,
                                                        contentColor = if (selectedPaymentMethod == key) Color.Black else Color.White
                                                    ),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.weight(1f).height(36.dp),
                                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                                                ) {
                                                    Text(lbl, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                }

                                if (selectedPaymentMethod == "Mixto") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = CardDark,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = cashAmountMixto,
                                                onValueChange = { input ->
                                                    // Permitir solo números y punto decimal
                                                    val filtered = input.filter { it.isDigit() || it == '.' }
                                                    if (filtered.count { it == '.' } <= 1) {
                                                        cashAmountMixto = filtered
                                                    }
                                                },
                                                label = { Text("💵 Efectivo (Bs)", fontSize = 11.sp) },
                                                placeholder = { Text("0.0", fontSize = 11.sp) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedContainerColor = SurfaceDark,
                                                    unfocusedContainerColor = SurfaceDark,
                                                    focusedBorderColor = AccentLime,
                                                    unfocusedBorderColor = BorderDark,
                                                    focusedTextColor = TextPrimary,
                                                    unfocusedTextColor = TextPrimary
                                                )
                                            )
                                            val efec = cashAmountMixto.toDoubleOrNull() ?: 0.0
                                            val saldoQR = maxOf(0.0, grandTotal - efec)
                                            val saldoFormatted = if (saldoQR % 1.0 == 0.0) saldoQR.toInt().toString() else String.format(Locale.US, "%.1f", saldoQR)
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("📱 Saldo QR:", color = TextSecondary, fontSize = 11.sp)
                                                Text("$saldoFormatted Bs", color = AccentLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                if (selectedPaymentMethod == "QR" || selectedPaymentMethod == "Mixto") {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (verifiedReceiptUrl != null) Color(0xFF162D20) else Color(0xFF1C2237),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (verifiedReceiptUrl != null) AccentGreen else if (isWaitingQrValidation) AccentLime else Color(0xFF2E3856)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "📸 Comprobante de Pago",
                                                    color = if (verifiedReceiptUrl != null) AccentGreen else AccentLime,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (!verifiedReceiptUrl.isNullOrBlank()) {
                                                    val urls = ReceiptImageUtils.splitReceiptUrls(verifiedReceiptUrl)
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = AccentGreen.copy(alpha = 0.2f),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen)
                                                    ) {
                                                        Text(
                                                            text = "✅ ${urls.size} Foto(s)",
                                                            color = AccentGreen,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            if (isWaitingQrValidation) {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    CircularProgressIndicator(color = AccentLime, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = qrValidationStatusMessage ?: "🔔 Solicitud enviada a celulares de Admin y Empleados...\nEsperando comprobante.",
                                                        color = AccentLime,
                                                        fontSize = 11.sp,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Button(
                                                        onClick = onCancelQrValidation,
                                                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed, contentColor = Color.White),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.height(30.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Cancelar Solicitud Móvil", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            } else if (!verifiedReceiptUrl.isNullOrBlank()) {
                                                val urls = ReceiptImageUtils.splitReceiptUrls(verifiedReceiptUrl)
                                                Column {
                                                    Text(
                                                        text = "Foto(s) del comprobante (${urls.size}) adjuntada(s) correctamente.",
                                                        color = TextPrimary,
                                                        fontSize = 11.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Button(
                                                            onClick = { showReceiptViewerForStarting = true },
                                                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                                                            shape = RoundedCornerShape(6.dp),
                                                            modifier = Modifier.weight(1.2f).height(32.dp),
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text("🖼️ Ver Fotos (${urls.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        if (onPickReceiptPhotos != null) {
                                                            Button(
                                                                onClick = onPickReceiptPhotos,
                                                                colors = ButtonDefaults.buttonColors(containerColor = CardDark, contentColor = AccentLime),
                                                                shape = RoundedCornerShape(6.dp),
                                                                modifier = Modifier.weight(1.1f).height(32.dp),
                                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Text("➕ Foto PC", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }

                                                        if (onUpdateReceiptPhotos != null) {
                                                            Button(
                                                                onClick = { onUpdateReceiptPhotos(null) },
                                                                colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.8f), contentColor = Color.White),
                                                                shape = RoundedCornerShape(6.dp),
                                                                modifier = Modifier.width(36.dp).height(32.dp),
                                                                contentPadding = PaddingValues(0.dp)
                                                            ) {
                                                                Text("🗑️", fontSize = 12.sp)
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            val price = priceText.toDoubleOrNull() ?: room.precioBaseSugerido
                                                            val duration = parseDurationMinutes(manualTimeText)
                                                            val efec = if (selectedPaymentMethod == "Mixto") (cashAmountMixto.toDoubleOrNull() ?: 0.0) else 0.0
                                                            val qr = if (selectedPaymentMethod == "QR") grandTotal else maxOf(0.0, grandTotal - efec)
                                                            onRequestQrValidation(
                                                                clientName.trim(),
                                                                clientPhone.trim(),
                                                                gameOrMovie.trim(),
                                                                duration,
                                                                price,
                                                                selectedPaymentMethod,
                                                                efec,
                                                                qr,
                                                                pendingOrders
                                                            )
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5), contentColor = Color.White),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1.3f).height(36.dp),
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("📱 Pedir al Celular", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    if (onPickReceiptPhotos != null) {
                                                        Button(
                                                            onClick = onPickReceiptPhotos,
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238), contentColor = AccentLime),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.weight(1.1f).height(36.dp),
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text("📁 Foto(s) de PC", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Sala Ocupada - Información en Vivo y Acciones
                                Text("🎮 Sesión Activa", color = AccentLime, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))

                                 Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CardDark,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "👤 Cliente: ${room.cliente ?: "N/A"} ${if (!room.celular.isNullOrEmpty()) "(${room.celular})" else ""}",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (onUpdateActiveRoomSession != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (isEditModeActive) AccentLime else Color(0xFF263238),
                                                    modifier = Modifier.clickable {
                                                        isEditModeActive = !isEditModeActive
                                                        if (isEditModeActive) {
                                                            editClientName = room.cliente ?: ""
                                                            editClientPhone = room.celular ?: ""
                                                            editGameOrMovie = room.pelicula ?: ""
                                                            editPriceText = room.precioCobrado.toString()
                                                            editPaymentMethod = room.metodoPago.ifBlank { "Efectivo" }
                                                            editExtraMinutesText = ""
                                                            editSaveSuccessMessage = null
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = if (isEditModeActive) "✕ Cancelar" else "✏️ Editar Parámetros",
                                                        color = if (isEditModeActive) Color.Black else AccentLime,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (!room.pelicula.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("🎬 Contenido: ${room.pelicula}", color = TextSecondary, fontSize = 13.sp, maxLines = 1)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("💳 Alquiler Cobrado: ${room.precioCobrado} Bs (${room.metodoPago})", color = TextSecondary, fontSize = 13.sp)

                                        if (!room.comprobanteUri.isNullOrBlank()) {
                                            val occupiedUrls = ReceiptImageUtils.splitReceiptUrls(room.comprobanteUri)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF14241B),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text("📸", fontSize = 14.sp)
                                                        Text("Comprobante (${occupiedUrls.size} foto(s))", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Button(
                                                            onClick = { showReceiptViewerForOccupied = true },
                                                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                                                            shape = RoundedCornerShape(4.dp),
                                                            modifier = Modifier.height(26.dp),
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp)
                                                        ) {
                                                            Text("🖼️ Ver Fotos (${occupiedUrls.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        if (onPickActiveRoomPhotos != null) {
                                                            Button(
                                                                onClick = { onPickActiveRoomPhotos(room.id) },
                                                                colors = ButtonDefaults.buttonColors(containerColor = CardDark, contentColor = AccentLime),
                                                                shape = RoundedCornerShape(4.dp),
                                                                modifier = Modifier.height(26.dp),
                                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp)
                                                            ) {
                                                                Text("➕ Foto", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else if (onPickActiveRoomPhotos != null) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Button(
                                                onClick = { onPickActiveRoomPhotos(room.id) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638), contentColor = AccentLime),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(28.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text("📁 Adjuntar Comprobante(s) desde PC", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                if (isEditModeActive && onUpdateActiveRoomSession != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF1E2638),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentLime),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text("✏️ Modificar Parámetros de la Sesión", color = AccentLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(8.dp))

                                            OutlinedTextField(
                                                value = editClientName,
                                                onValueChange = { editClientName = it },
                                                label = { Text("Nombre Cliente *", fontSize = 11.sp) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedContainerColor = CardDark,
                                                    unfocusedContainerColor = CardDark,
                                                    focusedBorderColor = AccentLime,
                                                    unfocusedBorderColor = BorderDark,
                                                    focusedTextColor = TextPrimary,
                                                    unfocusedTextColor = TextPrimary
                                                )
                                            )

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = editClientPhone,
                                                    onValueChange = { editClientPhone = it },
                                                    label = { Text("Celular", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedContainerColor = CardDark,
                                                        unfocusedContainerColor = CardDark,
                                                        focusedBorderColor = AccentLime,
                                                        unfocusedBorderColor = BorderDark,
                                                        focusedTextColor = TextPrimary,
                                                        unfocusedTextColor = TextPrimary
                                                    )
                                                )

                                                OutlinedTextField(
                                                    value = editGameOrMovie,
                                                    onValueChange = { editGameOrMovie = it },
                                                    label = { Text("Película / Juego", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedContainerColor = CardDark,
                                                        unfocusedContainerColor = CardDark,
                                                        focusedBorderColor = AccentLime,
                                                        unfocusedBorderColor = BorderDark,
                                                        focusedTextColor = TextPrimary,
                                                        unfocusedTextColor = TextPrimary
                                                    )
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = editPriceText,
                                                    onValueChange = { editPriceText = it },
                                                    label = { Text("Precio Alquiler (Bs)", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedContainerColor = CardDark,
                                                        unfocusedContainerColor = CardDark,
                                                        focusedBorderColor = AccentLime,
                                                        unfocusedBorderColor = BorderDark,
                                                        focusedTextColor = TextPrimary,
                                                        unfocusedTextColor = TextPrimary
                                                    )
                                                )

                                                OutlinedTextField(
                                                    value = editExtraMinutesText,
                                                    onValueChange = { editExtraMinutesText = it },
                                                    label = { Text("Ajustar Tiempo (+/- min)", fontSize = 11.sp) },
                                                    placeholder = { Text("Ej: 15 o -10", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedContainerColor = CardDark,
                                                        unfocusedContainerColor = CardDark,
                                                        focusedBorderColor = AccentLime,
                                                        unfocusedBorderColor = BorderDark,
                                                        focusedTextColor = TextPrimary,
                                                        unfocusedTextColor = TextPrimary
                                                    )
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Text("Método de Pago:", color = TextSecondary, fontSize = 11.sp)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                                val editMethods = listOf("Efectivo" to "💵 Efectivo", "QR" to "📱 QR", "Mixto" to "💵📱 Mixto")
                                                for ((k, l) in editMethods) {
                                                    val isSel = editPaymentMethod == k
                                                    Button(
                                                        onClick = { editPaymentMethod = k },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isSel) AccentLime else CardDark,
                                                            contentColor = if (isSel) Color.Black else Color.White
                                                        ),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.weight(1f).height(32.dp),
                                                        contentPadding = PaddingValues(2.dp)
                                                    ) {
                                                        Text(l, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                    }
                                                }
                                            }

                                            if (editSaveSuccessMessage != null) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(editSaveSuccessMessage!!, color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Button(
                                                onClick = performUpdateSession,
                                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                                enabled = !isActionInProgress && editClientName.isNotBlank()
                                            ) {
                                                if (isActionInProgress) {
                                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                Text("💾 Guardar Cambios en Sala", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Cronómetro en vivo y Horario de Salida
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = CardDark,
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentGreen),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("⏱️ TIEMPO RESTANTE", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = room.formatearTiempoRestante(),
                                            color = AccentGreen,
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "🕒 Inicio: ${room.obtenerHoraInicioFormateada()}",
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = "🏁 Hora Salida: ${room.obtenerHoraSalidaFormateada()}",
                                                color = AccentLime,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text("➕ Sumar Tiempo a la Sala:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val timeOptions = listOf(15 to "+15 min", 30 to "+30 min", 60 to "+1 hora")
                                    for ((mins, lbl) in timeOptions) {
                                        Button(
                                            onClick = { onAddTime(mins) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = CardDark,
                                                contentColor = AccentLime
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            contentPadding = PaddingValues(2.dp),
                                            enabled = !isActionInProgress
                                        ) {
                                            Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .background(CardDark, RoundedCornerShape(6.dp))
                                            .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (customExtraTimeText.isEmpty()) {
                                            Text("Ej: 20 min o 1:15", color = TextMuted, fontSize = 12.sp)
                                        }
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = customExtraTimeText,
                                            onValueChange = { customExtraTimeText = it },
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                color = TextPrimary,
                                                fontSize = 12.sp
                                            ),
                                            cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentLime),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val mins = parseDurationMinutes(customExtraTimeText)
                                            if (mins > 0) {
                                                onAddTime(mins)
                                                customExtraTimeText = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = Color.Black),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(48.dp),
                                        enabled = !isActionInProgress && customExtraTimeText.isNotBlank(),
                                        contentPadding = PaddingValues(horizontal = 14.dp)
                                    ) {
                                        Text("⏱️ Extender", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                Button(
                                    onClick = {
                                        if (!isActionInProgress && !isSubmittingAction) {
                                            isSubmittingAction = true
                                            onClearRoom()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AlertRed,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    enabled = !isActionInProgress && !isSubmittingAction
                                ) {
                                    if (isActionInProgress || isSubmittingAction) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text("🛑 Finalizar y Liberar Sala", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                }
                            }
                        }
                    }

                    // COLUMNA DERECHA: Consumos, Snacks, Escáner y Catálogo
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.weight(1.25f).fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🍿 Consumos y Snacks", color = AccentLime, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AccentGreen.copy(alpha = 0.15f)
                                ) {
                                    Text("⚡ Pistola Activa", color = AccentGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Buscador / Escáner directo
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = barcodeSearchInput,
                                    onValueChange = { barcodeSearchInput = it },
                                    placeholder = { Text("Escanear código o buscar snack...", fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                                                val found = catalogProducts.find { it.barcode == barcodeSearchInput.trim() || it.name.equals(barcodeSearchInput.trim(), ignoreCase = true) }
                                                if (found != null) {
                                                    onAddProduct(found)
                                                    barcodeSearchInput = ""
                                                    return@onKeyEvent true
                                                }
                                            }
                                            false
                                        },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = CardDark,
                                        unfocusedContainerColor = CardDark,
                                        focusedBorderColor = AccentLime,
                                        unfocusedBorderColor = BorderDark,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )

                                Button(
                                    onClick = {
                                        val found = catalogProducts.find { it.barcode == barcodeSearchInput.trim() || it.name.equals(barcodeSearchInput.trim(), ignoreCase = true) }
                                            ?: filteredCatalog.firstOrNull()
                                        if (found != null) {
                                            onAddProduct(found)
                                            barcodeSearchInput = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(50.dp)
                                ) {
                                    Text("➕ Añadir", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Catálogo rápido de productos
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                items(filteredCatalog.chunked(2).size) { rowIndex ->
                                    val pair = filteredCatalog.chunked(2)[rowIndex]
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        for (prod in pair) {
                                            val isProdOut = prod.currentStock <= 0
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isProdOut) Color(0xFF181B24) else CardDark,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isProdOut) AlertRed.copy(alpha = 0.3f) else BorderDark),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .then(if (isProdOut) Modifier.alpha(0.5f) else Modifier)
                                                    .clickable(enabled = !isProdOut) { onAddProduct(prod) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(prod.name, color = if (isProdOut) TextMuted else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text(if (isProdOut) "🚫 Agotado (0)" else "${prod.unitPrice} Bs (${prod.currentStock} disp.)", color = if (isProdOut) AlertRed else AccentLime, fontSize = 10.sp)
                                                    }
                                                    if (!isProdOut) {
                                                        Icon(Icons.Default.Add, contentDescription = "Agregar", tint = AccentGreen, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                        if (pair.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Lista de pedidos de la sala
                            Text("Lista de Pedidos (${currentOrders.size}):", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CardDark,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                                modifier = Modifier.weight(0.9f).fillMaxWidth()
                            ) {
                                if (currentOrders.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No se han agregado consumos a esta sala.", color = TextSecondary, fontSize = 12.sp)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                        itemsIndexed(currentOrders) { idx, ord ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp, horizontal = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${ord.cantidad}x ${ord.nombre}",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${ord.subtotal} Bs",
                                                    color = AccentLime,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp),
                                                    maxLines = 1
                                                )
                                                IconButton(
                                                    onClick = { onRemoveProduct(idx) },
                                                    modifier = Modifier.size(22.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = AlertRed, modifier = Modifier.size(15.dp))
                                                }
                                            }
                                            HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Pie del diálogo: Totales y Acciones Principales
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Tarifa Sala: ${basePrice} Bs", color = TextSecondary, fontSize = 13.sp)
                            Text("+ Consumos: ${totalConsumos} Bs", color = AccentLime, fontSize = 13.sp)
                            Text("TOTAL: ${grandTotal} Bs", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = CardDark, contentColor = TextPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(40.dp).widthIn(min = 100.dp)
                            ) {
                                Text("Cerrar", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }

                            if (!isOccupied) {
                                Button(
                                    onClick = performConfirmStart,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentGreen,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(40.dp).widthIn(min = 160.dp),
                                    enabled = !isActionInProgress && !isSubmittingAction && clientName.isNotBlank()
                                ) {
                                    if (isActionInProgress || isSubmittingAction) {
                                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text("▶ Iniciar Función", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQrVerificationPromptDialog) {
        AlertDialog(
            onDismissRequest = { showQrVerificationPromptDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📸 Comprobante Móvil Requerido", color = AccentLime, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Has seleccionado método de pago '$selectedPaymentMethod' pero aún no se ha adjuntado el comprobante fotográfico desde los teléfonos móviles.",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "¿Deseas solicitar la captura del comprobante al celular del personal antes de iniciar la función?",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showQrVerificationPromptDialog = false
                        val price = priceText.toDoubleOrNull() ?: room.precioBaseSugerido
                        val duration = parseDurationMinutes(manualTimeText)
                        val efec = if (selectedPaymentMethod == "Mixto") (cashAmountMixto.toDoubleOrNull() ?: 0.0) else 0.0
                        val qr = if (selectedPaymentMethod == "QR") grandTotal else maxOf(0.0, grandTotal - efec)
                        onRequestQrValidation(
                            clientName.trim(),
                            clientPhone.trim(),
                            gameOrMovie.trim(),
                            duration,
                            price,
                            selectedPaymentMethod,
                            efec,
                            qr,
                            pendingOrders
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5), contentColor = Color.White),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("📱 Solicitar Foto al Celular", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showQrVerificationPromptDialog = false }) {
                        Text("Cancelar", color = TextSecondary, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            showQrVerificationPromptDialog = false
                            val price = priceText.toDoubleOrNull() ?: room.precioBaseSugerido
                            val duration = parseDurationMinutes(manualTimeText)
                            onConfirmStart(
                                clientName.trim(),
                                clientPhone.trim(),
                                gameOrMovie.trim(),
                                duration,
                                price,
                                selectedPaymentMethod,
                                pendingOrders,
                                verifiedReceiptUrl
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CardDark, contentColor = WarningYellow),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("▶ Iniciar sin Foto", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            containerColor = Color(0xFF1A1C29)
        )
    }

    if (showReceiptViewerForStarting && !verifiedReceiptUrl.isNullOrBlank()) {
        ReceiptViewerDialog(
            receiptString = verifiedReceiptUrl,
            title = "Comprobante(s) de Inicio de Sala",
            onDeletePhoto = onUpdateReceiptPhotos,
            onDismiss = { showReceiptViewerForStarting = false }
        )
    }

    if (showReceiptViewerForOccupied && !room.comprobanteUri.isNullOrBlank()) {
        ReceiptViewerDialog(
            receiptString = room.comprobanteUri!!,
            title = "Comprobante(s) de Sesión - ${room.nombre}",
            onDeletePhoto = { newStr ->
                onUpdateActiveRoomPhotos?.invoke(room.id, newStr)
            },
            onDismiss = { showReceiptViewerForOccupied = false }
        )
    }
}




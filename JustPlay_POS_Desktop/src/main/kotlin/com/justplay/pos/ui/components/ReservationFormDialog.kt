package com.justplay.pos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import com.justplay.pos.core.models.Reservation
import com.justplay.pos.core.utils.ReceiptImageUtils
import com.justplay.pos.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReservationFormDialog(
    initialReservation: Reservation? = null,
    defaultCategory: String = "Minicine",
    availableCategories: List<String> = listOf("Minicine", "Autitos", "Just Dance"),
    onSaveReservation: (Reservation) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = initialReservation != null

    val todayStr = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    }
    val tomorrowStr = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
    }

    var clientName by remember { mutableStateOf(initialReservation?.nombre ?: "") }
    var clientPhone by remember { mutableStateOf(initialReservation?.celular ?: "") }
    var reservationDate by remember { mutableStateOf(initialReservation?.fecha?.ifBlank { todayStr } ?: todayStr) }

    val defaultHour = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.HOUR_OF_DAY, 1)
        SimpleDateFormat("HH:00", Locale.getDefault()).format(cal.time)
    }
    var reservationTime by remember { mutableStateOf(initialReservation?.horaProgramada?.ifBlank { defaultHour } ?: defaultHour) }

    var selectedCategory by remember {
        mutableStateOf(
            initialReservation?.tipoSala?.ifBlank { defaultCategory } ?: defaultCategory
        )
    }

    var durationText by remember { mutableStateOf(initialReservation?.duracion?.ifBlank { "1:00" } ?: "1:00") }
    var volantesCount by remember { mutableStateOf(initialReservation?.cantidadVolantes ?: 1) }

    var adelantoAmount by remember { mutableStateOf(initialReservation?.adelanto ?: "") }
    var adelantoMethod by remember { mutableStateOf(initialReservation?.metodoPagoAdelanto?.ifBlank { "Efectivo" } ?: "Efectivo") }
    var comprobanteUri by remember { mutableStateOf<String?>(initialReservation?.comprobanteUri) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isAutitos = selectedCategory.contains("aut", ignoreCase = true) ||
            selectedCategory.contains("volante", ignoreCase = true) ||
            selectedCategory.contains("simulador", ignoreCase = true)

    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .width(580.dp)
                .heightIn(max = 680.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp)
            ) {
                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(if (isEditing) "✏️" else "📝", fontSize = 22.sp)
                        Text(
                            text = if (isEditing) "Editar Reserva" else "Registrar Nueva Reserva",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Contenido desplazable
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (errorMessage != null) {
                        Surface(
                            color = AlertRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = AlertRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // 1. Datos del Cliente
                    Text("👤 Datos del Cliente", color = AccentLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = clientName,
                        onValueChange = { clientName = it; errorMessage = null },
                        label = { Text("Nombre del Cliente *") },
                        placeholder = { Text("Ej. Juan Pérez") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGreen,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = clientPhone,
                        onValueChange = { clientPhone = it },
                        label = { Text("Celular (Opcional)") },
                        placeholder = { Text("Ej. 70012345") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGreen,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 2. Programación (Fecha y Hora)
                    Text("🗓️ Programación de Fecha y Hora", color = AccentLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = reservationDate,
                            onValueChange = { reservationDate = it },
                            label = { Text("Fecha (dd/MM/yyyy)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGreen,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = { reservationDate = todayStr },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (reservationDate == todayStr) AccentGreen else BgDark,
                                contentColor = if (reservationDate == todayStr) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Hoy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { reservationDate = tomorrowStr },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (reservationDate == tomorrowStr) AccentGreen else BgDark,
                                contentColor = if (reservationDate == tomorrowStr) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Mañana", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = reservationTime,
                            onValueChange = { reservationTime = it },
                            label = { Text("Hora Programada (HH:mm)") },
                            placeholder = { Text("Ej. 16:00") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGreen,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Accesos rápidos de hora
                        listOf("14:00", "16:00", "18:00", "20:00").forEach { quickHour ->
                            Button(
                                onClick = { reservationTime = quickHour },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (reservationTime == quickHour) AccentLime else BgDark,
                                    contentColor = if (reservationTime == quickHour) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text(quickHour, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 3. Sección / Categoría
                    Text("🎮 ¿Qué desea reservar?", color = AccentLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val categories = remember(availableCategories) {
                            val base = listOf("Minicine", "Autitos", "Just Dance")
                            (base + availableCategories).distinct()
                        }

                        categories.forEach { cat ->
                            val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                            val icon = when {
                                cat.contains("cine", ignoreCase = true) -> "🎬"
                                cat.contains("aut", ignoreCase = true) || cat.contains("volante", ignoreCase = true) -> "🏎️"
                                cat.contains("dance", ignoreCase = true) -> "💃"
                                else -> "🎮"
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = cat },
                                label = { Text("$icon $cat", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGreen,
                                    selectedLabelColor = Color.Black,
                                    containerColor = BgDark,
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    // Duración y Volantes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = durationText,
                            onValueChange = { durationText = it },
                            label = { Text("⏱️ Duración (ej. 1:00, 1:30)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGreen,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        if (isAutitos) {
                            OutlinedTextField(
                                value = volantesCount.toString(),
                                onValueChange = { volantesCount = it.toIntOrNull()?.coerceIn(1, 10) ?: 1 },
                                label = { Text("🏎️ Volantes Reservados") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentGreen,
                                    unfocusedBorderColor = BorderDark,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 4. Adelanto y Método de Pago
                    Text("💰 Adelanto de Reserva", color = Color(0xFFFFD54F), fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = adelantoAmount,
                            onValueChange = { adelantoAmount = it },
                            label = { Text("Monto Adelanto (Bs)") },
                            placeholder = { Text("0") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFFD54F),
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Selector de método de pago del adelanto
                        listOf("Efectivo" to "💵 Efectivo", "QR" to "📱 QR", "Mixto" to "💵📱 Mixto").forEach { (key, label) ->
                            val isSelected = adelantoMethod.equals(key, ignoreCase = true)
                            Button(
                                onClick = { adelantoMethod = key },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color(0xFFFFD54F) else BgDark,
                                    contentColor = if (isSelected) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Comprobante
                    Surface(
                        color = BgDark,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF33374B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (!comprobanteUri.isNullOrBlank()) "✅ Comprobante adjuntado" else "📎 Comprobante de Adelanto",
                                    color = if (!comprobanteUri.isNullOrBlank()) AccentGreen else TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = if (!comprobanteUri.isNullOrBlank()) "Listo para registrar con la reserva" else "Opcional (Foto o captura de QR)",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!comprobanteUri.isNullOrBlank()) {
                                    IconButton(onClick = { comprobanteUri = null }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Quitar foto", tint = AlertRed)
                                    }
                                }

                                Button(
                                    onClick = {
                                        val files = ReceiptImageUtils.pickImagesFromNativeDialog("Seleccionar Comprobante de Reserva")
                                        if (files.isNotEmpty()) {
                                            val payload = ReceiptImageUtils.processAndCompressImageFile(files.first())
                                            comprobanteUri = payload
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentLime,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(if (comprobanteUri.isNullOrBlank()) "Examinar..." else "Cambiar...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Botones Cancelar / Guardar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancelar")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            if (clientName.trim().isBlank()) {
                                errorMessage = "⚠️ Por favor ingresa el nombre del cliente."
                                return@Button
                            }
                            if (reservationDate.trim().isBlank()) {
                                errorMessage = "⚠️ Por favor ingresa la fecha de la reserva."
                                return@Button
                            }
                            if (reservationTime.trim().isBlank()) {
                                errorMessage = "⚠️ Por favor ingresa la hora programada."
                                return@Button
                            }

                            val newRes = Reservation(
                                idFirebase = initialReservation?.idFirebase ?: UUID.randomUUID().toString(),
                                nombre = clientName.trim(),
                                celular = clientPhone.trim(),
                                fecha = reservationDate.trim(),
                                horaProgramada = reservationTime.trim(),
                                tipoSala = selectedCategory.trim(),
                                adelanto = adelantoAmount.trim(),
                                metodoPagoAdelanto = adelantoMethod.trim(),
                                comprobanteUri = comprobanteUri,
                                duracion = durationText.trim().ifBlank { "1:00" },
                                cantidadVolantes = if (isAutitos) volantesCount else 1,
                                asistio = initialReservation?.asistio ?: false,
                                horaLlegada = initialReservation?.horaLlegada
                            )
                            onSaveReservation(newRes)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(if (isEditing) "💾 Guardar Cambios" else "💾 Registrar Reserva", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

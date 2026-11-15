package com.justplay.pos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justplay.pos.core.models.Reservation
import com.justplay.pos.core.models.Room
import com.justplay.pos.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RoomReservationsSidebar(
    category: String,
    reservations: List<Reservation>,
    availableRooms: List<Room>,
    onNewReservation: () -> Unit,
    onEditReservation: (Reservation) -> Unit,
    onDeleteReservation: (String) -> Unit,
    onMarkAttendance: (Reservation) -> Unit,
    onPassToRoom: (Reservation) -> Unit,
    onViewReceipt: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedDateFilter by remember { mutableStateOf("TODAS") } // "HOY", "PROXIMAS", "TODAS"
    var reservationToDelete by remember { mutableStateOf<Reservation?>(null) }

    val todayStr = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    }

    fun parseDateSafe(str: String): Long {
        return try {
            val parts = str.trim().split("/")
            if (parts.size >= 3) {
                val d = parts[0].toIntOrNull() ?: 1
                val m = parts[1].toIntOrNull() ?: 1
                val y = parts[2].toIntOrNull() ?: 2026
                val cal = Calendar.getInstance()
                cal.set(y, m - 1, d, 0, 0, 0)
                cal.timeInMillis
            } else 0L
        } catch (_: Exception) { 0L }
    }

    val todayMillis = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

    fun matchesCategory(res: Reservation, cat: String): Boolean {
        val c = cat.trim().lowercase()
        val r = res.tipoSala.trim().lowercase()
        if (c == "todas" || c.isBlank()) return true
        if (c == "minicine" || c == "salas") {
            return r.contains("cine") || r.contains("sala") || (r.isBlank() && res.cantidadVolantes <= 1 && !r.contains("aut") && !r.contains("dance"))
        }
        if (c == "autitos" || c == "volantes") {
            return r.contains("aut") || r.contains("volante") || r.contains("car") || r.contains("simulador") || res.cantidadVolantes > 1
        }
        if (c == "just dance" || c.contains("dance")) {
            return r.contains("dance")
        }
        return r.equals(c, ignoreCase = true) || r.contains(c) || c.contains(r)
    }

    val filteredReservations = remember(reservations, category, selectedDateFilter) {
        reservations
            .filter { matchesCategory(it, category) }
            .filter { res ->
                when (selectedDateFilter) {
                    "HOY" -> res.fecha.trim() == todayStr
                    "PROXIMAS" -> {
                        val m = parseDateSafe(res.fecha)
                        m >= todayMillis || res.fecha.trim() == todayStr
                    }
                    else -> true
                }
            }
            .sortedWith(
                compareBy(
                    { parseDateSafe(it.fecha) },
                    { it.horaProgramada }
                )
            )
    }

    // Modal de confirmación para eliminar
    if (reservationToDelete != null) {
        val res = reservationToDelete!!
        AlertDialog(
            onDismissRequest = { reservationToDelete = null },
            title = { Text("Eliminar Reserva", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "¿Estás seguro de que deseas eliminar la reserva de '${res.nombre}' para las ${res.horaProgramada} (${res.fecha})?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteReservation(res.idFirebase)
                        reservationToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed, contentColor = Color.White)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { reservationToDelete = null }) {
                    Text("Cancelar")
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(12.dp)
        )
    }

    Surface(
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        modifier = modifier
            .fillMaxHeight()
            .width(380.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // 1. Cabecera con Título y Botón Nueva Reserva
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val catIcon = when {
                        category.contains("cine", ignoreCase = true) -> "🎬"
                        category.contains("aut", ignoreCase = true) || category.contains("volante", ignoreCase = true) -> "🏎️"
                        category.contains("dance", ignoreCase = true) -> "💃"
                        else -> "🎮"
                    }
                    Text(
                        text = "$catIcon Reservas (${filteredReservations.size})",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sección: $category",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = onNewReservation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Registrar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Filtros de Fecha Rápidos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "HOY" to "Hoy",
                    "PROXIMAS" to "Próximas",
                    "TODAS" to "Todas (${reservations.count { matchesCategory(it, category) }})"
                ).forEach { (key, label) ->
                    val isSel = selectedDateFilter == key
                    FilterChip(
                        selected = isSel,
                        onClick = { selectedDateFilter = key },
                        label = { Text(label, fontSize = 10.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentLime,
                            selectedLabelColor = Color.Black,
                            containerColor = BgDark,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSel,
                            borderColor = if (isSel) AccentLime else BorderDark
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Lista de Tarjetas de Reserva
            if (filteredReservations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🗓️", fontSize = 38.sp)
                        Text(
                            text = "No hay reservas para esta sección",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        OutlinedButton(
                            onClick = onNewReservation,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentLime),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentLime),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("➕ Registrar Reserva Ahora", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredReservations, key = { it.idFirebase }) { res ->
                        ReservationItemCard(
                            reservation = res,
                            availableRoomsCount = availableRooms.size,
                            onPassToRoom = { onPassToRoom(res) },
                            onMarkAttendance = { onMarkAttendance(res) },
                            onEdit = { onEditReservation(res) },
                            onDelete = { reservationToDelete = res },
                            onViewReceipt = onViewReceipt
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationItemCard(
    reservation: Reservation,
    availableRoomsCount: Int,
    onPassToRoom: () -> Unit,
    onMarkAttendance: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewReceipt: ((String) -> Unit)?
) {
    Surface(
        color = BgDark,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (reservation.asistio) AccentGreen.copy(alpha = 0.6f) else BorderDark
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Fila superior: Hora, Fecha y Badge de tipo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color(0xFF2C2F40),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "⏰ ${reservation.horaProgramada}",
                            color = AccentLime,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "🗓️ ${reservation.fecha}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    color = AccentGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = reservation.tipoSala,
                        color = AccentGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Nombre del cliente
            Text(
                text = reservation.nombre,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Detalles: Celular y Duración
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📞 ${if (reservation.celular.isNotBlank()) reservation.celular else "Sin celular"}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = "⏱️ ${reservation.duracion}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            // Volantes si es autitos
            if (reservation.tipoSala.contains("aut", ignoreCase = true) ||
                reservation.tipoSala.contains("volante", ignoreCase = true) ||
                reservation.cantidadVolantes > 1
            ) {
                Text(
                    text = "🏎️ Volantes reservados: ${reservation.cantidadVolantes}",
                    color = Color(0xFF81D4FA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Adelanto (si tiene)
            if (reservation.adelanto.isNotBlank() && reservation.adelanto != "0") {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF262A20),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4C5432)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val metodo = if (reservation.metodoPagoAdelanto.equals("QR", ignoreCase = true)) "📱 QR" else "💵 Efectivo"
                        Text(
                            text = "💰 Adelanto: ${reservation.adelanto} Bs ($metodo)",
                            color = Color(0xFFFFD54F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (!reservation.comprobanteUri.isNullOrBlank() && onViewReceipt != null) {
                            TextButton(
                                onClick = { onViewReceipt(reservation.comprobanteUri!!) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("🖼️ Ver Foto", fontSize = 10.sp, color = AccentLime, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Estado de Asistencia
            if (reservation.asistio) {
                Surface(
                    color = Color(0xFF1E3A2B),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✅ Llegó a las: ${reservation.horaLlegada ?: "Registrado"}",
                        color = AccentLime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onMarkAttendance,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF81D4FA)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF81D4FA).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("🙋 Marcar Llegada", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botones de Acción: Pasar a Sala, Editar, Eliminar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPassToRoom,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("🚀 Pasar a Sala", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = AlertRed.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

package com.justplay.pos.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justplay.pos.core.models.Room
import com.justplay.pos.core.models.RoomState
import com.justplay.pos.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun RoomDashboardPanel(
    rooms: List<Room>,
    category: String, // "Minicine", "Autitos", "Just Dance" o "Todas"
    onRoomClick: (Room) -> Unit,
    onRefreshRooms: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Tick para actualizar los cronómetros en tiempo real
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick = System.currentTimeMillis()
        }
    }

    val filteredRooms = remember(rooms, category, tick) {
        when (category) {
            "Minicine" -> rooms.filter { it.categoria == "Minicine" }
            "Autitos" -> rooms.filter { it.categoria == "Autitos" }
            "Just Dance" -> rooms.filter { it.categoria == "Just Dance" }
            else -> rooms
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Cabecera de la sección
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val titleIcon = when (category) {
                "Minicine" -> "🎬 Salas de Minicine"
                "Autitos" -> "🏎️ Simuladores y Volantes (Autitos)"
                "Just Dance" -> "💃 Salas Just Dance"
                else -> "🎮 Todas las Salas"
            }
            Text(
                text = "$titleIcon (${filteredRooms.size})",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onRefreshRooms,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceDark,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text("🔄 Sincronizar", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredRooms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay salas configuradas en esta sección", color = TextSecondary, fontSize = 14.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredRooms) { room ->
                    RoomCard(
                        room = room,
                        onClick = { onRoomClick(room) }
                    )
                }
            }
        }
    }
}

@Composable
fun RoomCard(
    room: Room,
    onClick: () -> Unit
) {
    val isOccupied = room.estado == RoomState.OCUPADA
    val remainingMillis = room.calcularTiempoRestanteMillis()
    val isTimeExpiring = isOccupied && remainingMillis in 1..300000L // Menos de 5 minutos

    val borderColor = when {
        !isOccupied -> BorderDark
        isTimeExpiring -> WarningYellow
        remainingMillis == 0L && (room.endTimeMillis ?: 0L) > 0L -> AlertRed
        else -> AccentGreen
    }

    val iconPrefix = when (room.categoria) {
        "Minicine" -> "🎬"
        "Autitos" -> "🏎️"
        else -> "💃"
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Cabecera de la tarjeta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$iconPrefix ${room.nombre}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isOccupied) AlertRed.copy(alpha = 0.2f) else AccentGreen.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (isOccupied) "OCUPADA" else "DISPONIBLE",
                        color = if (isOccupied) AlertRed else AccentGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isOccupied) {
                // Información de la sesión activa
                Text(
                    text = "👤 Cliente: ${room.cliente ?: "N/A"} ${if (!room.celular.isNullOrEmpty()) "(${room.celular})" else ""}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (!room.pelicula.isNullOrEmpty()) {
                    Text(
                        text = "🎮 Contenido: ${room.pelicula}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Horario de Inicio y Salida / Finalización
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceDark.copy(alpha = 0.7f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🕒 Inicio: ${room.obtenerHoraInicioFormateada()}",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "🏁 Salida: ${room.obtenerHoraSalidaFormateada()}",
                            color = AccentLime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Contador de Tiempo en Vivo
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⏱️ Tiempo Restante:",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = room.formatearTiempoRestante(),
                            color = if (isTimeExpiring || (remainingMillis == 0L && (room.endTimeMillis ?: 0L) > 0L)) AlertRed else AccentGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Resumen de Pedidos si existen
                if (room.pedidos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "🍿 Consumos: ${room.pedidos.size} item(s) (${room.totalPedidos} Bs)",
                        color = AccentLime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                // Sala Disponible
                Text(
                    text = "Tarifa base: ${room.precioBaseSugerido} Bs (1 hora)",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

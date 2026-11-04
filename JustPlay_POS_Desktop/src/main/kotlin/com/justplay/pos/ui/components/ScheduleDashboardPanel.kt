package com.justplay.pos.ui.components

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import com.justplay.pos.core.models.Reservation
import com.justplay.pos.core.models.Room
import com.justplay.pos.core.models.RoomState
import com.justplay.pos.core.models.SessionRecord
import com.justplay.pos.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ScheduleDashboardPanel(
    rooms: List<Room>,
    reservations: List<Reservation>,
    records: List<SessionRecord> = emptyList(),
    selectedDate: String,
    categoryFilter: String,
    viewMode: String,
    onDateChange: (String) -> Unit,
    onCategoryFilterChange: (String) -> Unit,
    onViewModeChange: (String) -> Unit,
    onRoomClick: (Room) -> Unit,
    onRefresh: () -> Unit
) {
    val sdfDay = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val todayStr = remember { sdfDay.format(Date()) }

    val tomorrowStr = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        sdfDay.format(cal.time)
    }

    val yesterdayStr = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        sdfDay.format(cal.time)
    }

    fun extractRoomNumber(name: String, id: Int): Int {
        val num = Regex("""\d+""").find(name)?.value?.toIntOrNull()
        return num ?: id
    }

    // Filtrar salas según categoría y ordenar secuencialmente empezando por Sala 1..N
    val filteredRooms = remember(rooms, categoryFilter) {
        val baseList = when (categoryFilter) {
            "MINICINE" -> rooms.filter { it.categoria == "Minicine" }
            "VOLANTES" -> rooms.filter { it.categoria == "Autitos" }
            "JUST_DANCE" -> rooms.filter { it.categoria == "Just Dance" }
            else -> rooms
        }
        baseList.sortedWith(compareBy({ extractRoomNumber(it.nombre, it.id) }, { it.id }))
    }

    // Normalizador de fechas para comparar dd/MM/yyyy con dd-MM-yyyy y yyyy-MM-dd
    val isAllDates = selectedDate == "TODAS"
    val currentDateStr = if (selectedDate.isBlank()) todayStr else selectedDate
    val normCurrentDate = remember(currentDateStr) { normalizeDate(currentDateStr) }

    // Filtrar reservas por fecha y categoría (o ver todas si isAllDates es true)
    val filteredReservations = remember(reservations, normCurrentDate, categoryFilter, isAllDates) {
        reservations.filter { res ->
            val resNormDate = normalizeDate(res.fecha)
            val matchDate = isAllDates || res.fecha.isBlank() || resNormDate == normCurrentDate || res.fecha == currentDateStr || res.fecha == currentDateStr.replace("/", "-")
            
            val isAutitos = res.tipoSala.contains("Auto", ignoreCase = true) || 
                            res.tipoSala.contains("Volante", ignoreCase = true) || 
                            res.tipoSala.contains("Car", ignoreCase = true) || 
                            res.tipoSala.contains("Simulador", ignoreCase = true) || 
                            res.cantidadVolantes > 1
            val isDance = res.tipoSala.contains("Dance", ignoreCase = true)
            val isMinicine = res.tipoSala.contains("Cine", ignoreCase = true) || 
                             res.tipoSala.contains("Minicine", ignoreCase = true) || 
                             res.tipoSala.contains("Sala", ignoreCase = true) || 
                             (!isAutitos && !isDance)

            val matchCat = when (categoryFilter) {
                "MINICINE" -> isMinicine
                "VOLANTES" -> isAutitos
                "JUST_DANCE" -> isDance
                else -> true
            }
            matchDate && matchCat
        }
    }

    // Filtrar registros finalizados del día por fecha y categoría (no borrados)
    val filteredRecords = remember(records, normCurrentDate, categoryFilter, isAllDates) {
        records.filter { rec ->
            if (rec.eliminado) return@filter false
            val recDate = rec.extraerFecha()
            val recNormDate = normalizeDate(recDate)
            val matchDate = isAllDates || recDate.isBlank() || recNormDate == normCurrentDate || recDate == currentDateStr || recDate == currentDateStr.replace("/", "-")
            
            val isAutitos = rec.nombreElemento.contains("Auto", ignoreCase = true) || 
                            rec.nombreElemento.contains("Volante", ignoreCase = true) || 
                            rec.nombreElemento.contains("Car", ignoreCase = true) || 
                            rec.nombreElemento.contains("Simulador", ignoreCase = true) || 
                            (rec.cantidadVolantes != null && rec.cantidadVolantes > 1)
            val isDance = rec.nombreElemento.contains("Dance", ignoreCase = true)
            val isMinicine = rec.nombreElemento.contains("Cine", ignoreCase = true) || 
                             rec.nombreElemento.contains("Minicine", ignoreCase = true) || 
                             rec.nombreElemento.contains("Sala", ignoreCase = true) || 
                             (!isAutitos && !isDance)

            val matchCat = when (categoryFilter) {
                "MINICINE" -> isMinicine
                "VOLANTES" -> isAutitos
                "JUST_DANCE" -> isDance
                else -> true
            }
            matchDate && matchCat
        }
    }

    // Métricas para KPI
    val activeOccupiedRooms = rooms.filter { it.estado == RoomState.OCUPADA }
    val nextEndingRoom = activeOccupiedRooms.minByOrNull { it.endTimeMillis ?: Long.MAX_VALUE }
    val totalVolantesInUse = activeOccupiedRooms.filter { it.categoria == "Autitos" }.size

    val countCines = remember(rooms) { rooms.count { it.categoria == "Minicine" } }
    val countVolantes = remember(rooms) { rooms.count { it.categoria == "Autitos" } }
    val countDance = remember(rooms) { rooms.count { it.categoria == "Just Dance" } }

    var exportNotification by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {
        // 1. Barra Superior de Control y Filtros
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Título y Selector de Fechas
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = AccentLime, modifier = Modifier.size(20.dp))
                        Text(
                            text = "TABLERO DE HORARIOS Y RESERVAS",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Chips de Fecha Rápida (Ayer, Hoy, Mañana, Todas)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val isAyer = !isAllDates && currentDateStr == yesterdayStr
                        FilterChip(
                            selected = isAyer,
                            onClick = { onDateChange(yesterdayStr) },
                            label = { Text("Ayer ($yesterdayStr)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentLime,
                                selectedLabelColor = Color.Black,
                                containerColor = CardDark,
                                labelColor = TextPrimary
                            )
                        )

                        val isHoy = !isAllDates && currentDateStr == todayStr
                        FilterChip(
                            selected = isHoy,
                            onClick = { onDateChange(todayStr) },
                            label = { Text("Hoy ($todayStr)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen,
                                selectedLabelColor = Color.Black,
                                containerColor = CardDark,
                                labelColor = TextPrimary
                            )
                        )

                        val isManana = !isAllDates && currentDateStr == tomorrowStr
                        FilterChip(
                            selected = isManana,
                            onClick = { onDateChange(tomorrowStr) },
                            label = { Text("Mañana ($tomorrowStr)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentLime,
                                selectedLabelColor = Color.Black,
                                containerColor = CardDark,
                                labelColor = TextPrimary
                            )
                        )

                        FilterChip(
                            selected = isAllDates,
                            onClick = { onDateChange(if (isAllDates) todayStr else "TODAS") },
                            label = { Text("📅 Todas (${reservations.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF64B5F6),
                                selectedLabelColor = Color.Black,
                                containerColor = CardDark,
                                labelColor = TextPrimary
                            )
                        )
                    }
                }

                // Filtro de Categoría, Selector de Vista, Botón Excel y Botón Refrescar
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Selector de Categoría
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            "TODOS" to "🎮 Todos (${rooms.size})",
                            "MINICINE" to "🎬 Cines ($countCines)",
                            "VOLANTES" to "🏎️ Volantes ($countVolantes)",
                            "JUST_DANCE" to "💃 Dance ($countDance)"
                        ).forEach { (key, label) ->
                            val isSelected = categoryFilter == key
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) AccentLime else CardDark,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) AccentLime else BorderDark),
                                modifier = Modifier.clickable { onCategoryFilterChange(key) }
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.Black else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    VerticalDivider(modifier = Modifier.height(24.dp), color = BorderDark)

                    // Selector de Vista (Cuadrícula Excel vs Tabla)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { onViewModeChange("GRID") },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (viewMode == "GRID") AccentGreen else CardDark,
                                contentColor = if (viewMode == "GRID") Color.Black else TextPrimary
                            ),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.GridView, contentDescription = "Cuadrícula Excel", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { onViewModeChange("TABLE") },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (viewMode == "TABLE") AccentGreen else CardDark,
                                contentColor = if (viewMode == "TABLE") Color.Black else TextPrimary
                            ),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.TableRows, contentDescription = "Tabla Resumen", modifier = Modifier.size(18.dp))
                        }
                    }

                    // Botón Exportar Reporte a Excel
                    Button(
                        onClick = {
                            try {
                                val (xlsxRec, _) = com.justplay.pos.services.ExcelReportExporter.exportSessions(records)
                                val (xlsxRes, _) = com.justplay.pos.services.ExcelReportExporter.exportReservations(reservations)
                                exportNotification = "✅ Reportes exportados a:\n${xlsxRec.name} y ${xlsxRes.name}\nCarpeta: ${xlsxRec.parentFile?.absolutePath}"
                            } catch (e: Exception) {
                                exportNotification = "❌ Error al exportar: ${e.message}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("📊 Excel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = onRefresh,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = CardDark, contentColor = AccentLime),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (exportNotification != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = Color(0xFF14291D),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = exportNotification ?: "",
                        color = AccentLime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { exportNotification = null }, modifier = Modifier.size(24.dp)) {
                        Text("✕", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Tarjetas de Resumen KPI
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Salas Ocupadas Ahora
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CardDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (activeOccupiedRooms.isNotEmpty()) AccentGreen else BorderDark),
                modifier = Modifier.weight(1f)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🟢", fontSize = 20.sp)
                    Column {
                        Text("SALAS EN SESIÓN", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${activeOccupiedRooms.size} activas", color = if (activeOccupiedRooms.isNotEmpty()) AccentGreen else TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            // Card 2: Próxima Salida
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CardDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                modifier = Modifier.weight(1.3f)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⏱️", fontSize = 20.sp)
                    Column {
                        Text("PRÓXIMA SALIDA", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        val proxTxt = if (nextEndingRoom != null) {
                            "${nextEndingRoom.nombre} (${nextEndingRoom.obtenerHoraSalidaFormateada()}) [${nextEndingRoom.formatearTiempoRestante()}]"
                        } else "Sin salidas pendientes"
                        Text(proxTxt, color = AccentLime, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Card 3: Reservas para la Fecha Seleccionada
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CardDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                modifier = Modifier.weight(1f)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📅", fontSize = 20.sp)
                    Column {
                        Text("RESERVAS ($currentDateStr)", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${filteredReservations.size} agendadas", color = Color(0xFF64B5F6), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            // Card 4: Volantes / Autitos
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CardDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                modifier = Modifier.weight(1f)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🏎️", fontSize = 20.sp)
                    Column {
                        Text("VOLANTES EN PISTA", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        val resVolantes = filteredReservations.sumOf { it.cantidadVolantes }
                        Text("$totalVolantesInUse activos | $resVolantes res.", color = WarningYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Contenido Principal: Cuadrícula Horario Tipo Excel O Tabla Resumen
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (viewMode == "GRID") {
                ScheduleExcelGridView(
                    rooms = filteredRooms,
                    reservations = filteredReservations,
                    records = filteredRecords,
                    categoryFilter = categoryFilter,
                    isToday = currentDateStr == todayStr,
                    onRoomClick = onRoomClick
                )
            } else {
                ScheduleTableView(
                    rooms = filteredRooms,
                    reservations = filteredReservations,
                    records = filteredRecords,
                    onRoomClick = onRoomClick
                )
            }
        }
    }
}

/**
 * 📊 Cuadrícula Horario Visual Tipo Excel con Despliegue Vertical
 * Filas: Intervalos de Horas (10:00 a 22:00)
 * Bloques organizados verticalmente por categoría para visualización completa sin scroll horizontal.
 */
@Composable
private fun ScheduleExcelGridView(
    rooms: List<Room>,
    reservations: List<Reservation>,
    records: List<SessionRecord>,
    categoryFilter: String,
    isToday: Boolean,
    onRoomClick: (Room) -> Unit
) {
    val scrollVertical = rememberScrollState()

    var showFull24h by remember { mutableStateOf(false) }

    val hourSlots = remember(reservations, records, showFull24h) {
        if (showFull24h) {
            (0..23).map { h ->
                val start = String.format("%02d:00", h)
                val end = String.format("%02d:00", (h + 1) % 24)
                h to "$start - $end"
            }
        } else {
            var minHour = 10
            var maxHour = 21

            reservations.forEach { res ->
                val hStart = res.horaProgramada.trim().take(5).split(":").getOrNull(0)?.toIntOrNull()
                if (hStart != null && hStart in 0..23) {
                    if (hStart < minHour) minHour = hStart
                    if (hStart > maxHour) maxHour = hStart
                }
                val hEnd = res.calcularHoraFin().take(5).split(":").getOrNull(0)?.toIntOrNull()
                if (hEnd != null && hEnd in 0..23) {
                    if (hEnd > maxHour) maxHour = hEnd
                }
            }
            records.forEach { rec ->
                val startMin = rec.extraerHoraInicioMinutos()
                if (startMin != null) {
                    val hStart = (startMin / 60).coerceIn(0, 23)
                    if (hStart < minHour) minHour = hStart
                    if (hStart > maxHour) maxHour = hStart
                    val hEnd = ((startMin + rec.parsearDuracionMinutos()) / 60).coerceIn(0, 23)
                    if (hEnd > maxHour) maxHour = hEnd
                }
            }

            (minHour..maxHour).map { h ->
                val start = String.format("%02d:00", h)
                val end = String.format("%02d:00", (h + 1) % 24)
                h to "$start - $end"
            }
        }
    }

    val minicines = remember(rooms) { rooms.filter { it.categoria == "Minicine" } }
    val volantes = remember(rooms) { rooms.filter { it.categoria == "Autitos" } }
    val justDance = remember(rooms) { rooms.filter { it.categoria == "Just Dance" } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 14.dp)
                .verticalScroll(scrollVertical)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Horario: ${hourSlots.firstOrNull()?.second?.take(5) ?: "10:00"} a ${hourSlots.lastOrNull()?.second?.takeLast(5) ?: "22:00"} (${hourSlots.size} hrs)",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (showFull24h) AccentLime.copy(alpha = 0.2f) else CardDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (showFull24h) AccentLime else BorderDark),
                    modifier = Modifier.clickable { showFull24h = !showFull24h }
                ) {
                    Text(
                        text = if (showFull24h) "🕒 Vista 24h ✓" else "🕒 Ver 24 Horas",
                        color = if (showFull24h) AccentLime else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            when (categoryFilter) {
                "MINICINE" -> {
                    CategoryScheduleGrid(
                        categoryTitle = "SALAS DE MINICINE",
                        categoryIcon = "🎬",
                        categoryBadgeColor = Color(0xFF90CAF9),
                        categoryBadgeBg = Color(0xFF162544),
                        categoryRooms = minicines,
                        reservations = reservations,
                        records = records,
                        hourSlots = hourSlots,
                        isToday = isToday,
                        onRoomClick = onRoomClick
                    )
                }
                "VOLANTES" -> {
                    CategoryScheduleGrid(
                        categoryTitle = "SIMULADORES Y VOLANTES",
                        categoryIcon = "🏎️",
                        categoryBadgeColor = WarningYellow,
                        categoryBadgeBg = Color(0xFF332211),
                        categoryRooms = volantes,
                        reservations = reservations,
                        records = records,
                        hourSlots = hourSlots,
                        isToday = isToday,
                        onRoomClick = onRoomClick
                    )
                }
                "JUST_DANCE" -> {
                    CategoryScheduleGrid(
                        categoryTitle = "SALAS DE JUST DANCE",
                        categoryIcon = "💃",
                        categoryBadgeColor = Color(0xFFF48FB1),
                        categoryBadgeBg = Color(0xFF33142B),
                        categoryRooms = justDance,
                        reservations = reservations,
                        records = records,
                        hourSlots = hourSlots,
                        isToday = isToday,
                        onRoomClick = onRoomClick
                    )
                }
                else -> { // "TODOS" - Despliegue vertical ordenado de todas las categorías
                    if (minicines.isNotEmpty()) {
                        CategoryScheduleGrid(
                            categoryTitle = "SALAS DE MINICINE",
                            categoryIcon = "🎬",
                            categoryBadgeColor = Color(0xFF90CAF9),
                            categoryBadgeBg = Color(0xFF162544),
                            categoryRooms = minicines,
                            reservations = reservations,
                            records = records,
                            hourSlots = hourSlots,
                            isToday = isToday,
                            onRoomClick = onRoomClick
                        )
                    }

                    if (volantes.isNotEmpty()) {
                        CategoryScheduleGrid(
                            categoryTitle = "SIMULADORES Y VOLANTES",
                            categoryIcon = "🏎️",
                            categoryBadgeColor = WarningYellow,
                            categoryBadgeBg = Color(0xFF332211),
                            categoryRooms = volantes,
                            reservations = reservations,
                            records = records,
                            hourSlots = hourSlots,
                            isToday = isToday,
                            onRoomClick = onRoomClick
                        )
                    }

                    if (justDance.isNotEmpty()) {
                        CategoryScheduleGrid(
                            categoryTitle = "SALAS DE JUST DANCE",
                            categoryIcon = "💃",
                            categoryBadgeColor = Color(0xFFF48FB1),
                            categoryBadgeBg = Color(0xFF33142B),
                            categoryRooms = justDance,
                            reservations = reservations,
                            records = records,
                            hourSlots = hourSlots,
                            isToday = isToday,
                            onRoomClick = onRoomClick
                        )
                    }
                }
            }
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollVertical)
        )
    }
}

@Composable
private fun CategoryScheduleGrid(
    categoryTitle: String,
    categoryIcon: String,
    categoryBadgeColor: Color,
    categoryBadgeBg: Color,
    categoryRooms: List<Room>,
    reservations: List<Reservation>,
    records: List<SessionRecord>,
    hourSlots: List<Pair<Int, String>>,
    isToday: Boolean,
    onRoomClick: (Room) -> Unit
) {
    if (categoryRooms.isEmpty()) return

    val headerTimeWidth = 105.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(Color(0xFF141724), RoundedCornerShape(10.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        // Banner de Categoría
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(categoryBadgeBg, RoundedCornerShape(8.dp))
                .border(1.dp, categoryBadgeColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(categoryIcon, fontSize = 16.sp)
                Text(
                    text = "$categoryTitle (${categoryRooms.size})",
                    color = categoryBadgeColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = "${categoryRooms.count { it.estado == RoomState.OCUPADA }} en sesión activa",
                color = AccentLime,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Encabezado de Columnas (Horario + Salas distribuidas al 100% de ancho)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E2337), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
        ) {
            Box(
                modifier = Modifier
                    .width(headerTimeWidth)
                    .height(40.dp)
                    .background(Color(0xFF141724))
                    .border(0.5.dp, BorderDark)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("⏰ HORARIO", color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            categoryRooms.forEach { room ->
                val titleColor = when (room.categoria) {
                    "Minicine" -> Color.White
                    "Autitos" -> WarningYellow
                    else -> Color(0xFFF48FB1)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(Color(0xFF1A2138))
                        .border(0.5.dp, BorderDark)
                        .clickable { onRoomClick(room) }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$categoryIcon ${room.nombre}",
                        color = titleColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Filas por cada intervalo de hora (10:00 a 22:00)
        hourSlots.forEach { (hourInt, hourRange) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, Color(0xFF262A3D))
            ) {
                // Celda de Hora
                Box(
                    modifier = Modifier
                        .width(headerTimeWidth)
                        .height(72.dp)
                        .background(Color(0xFF161826))
                        .border(0.5.dp, BorderDark)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = hourRange,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                // Celdas de cada sala
                categoryRooms.forEach { room ->
                    val isOccupiedInSlot = isRoomOccupiedInHourSlot(room, hourInt, isToday)
                    val matchingFinishedRecords = records.filter { rec ->
                        isRecordInHourSlot(rec, room, categoryRooms, hourInt)
                    }
                    val matchingReservations = reservations.filter { res ->
                        isReservationInHourSlot(res, room, categoryRooms, hourInt)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .background(
                                when {
                                    isOccupiedInSlot -> Color(0xFF143322)
                                    matchingFinishedRecords.isNotEmpty() -> Color(0xFF1B2332)
                                    matchingReservations.isNotEmpty() -> Color(0xFF132B45)
                                    else -> CardDark.copy(alpha = 0.5f)
                                }
                            )
                            .border(
                                0.5.dp,
                                when {
                                    isOccupiedInSlot -> AccentGreen
                                    matchingFinishedRecords.isNotEmpty() -> Color(0xFF4A5568)
                                    matchingReservations.isNotEmpty() -> Color(0xFF42A5F5)
                                    else -> BorderDark
                                }
                            )
                            .clickable { onRoomClick(room) }
                            .padding(4.dp)
                    ) {
                        when {
                            isOccupiedInSlot -> {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🟢 EN VIVO",
                                            color = AccentGreen,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "🏁 ${room.obtenerHoraSalidaFormateada()}",
                                            color = AccentLime,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        text = "👤 ${room.cliente ?: "Cliente"}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (!room.pelicula.isNullOrBlank()) {
                                        Text(
                                            text = "🎬 ${room.pelicula}",
                                            color = TextSecondary,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else if (room.categoria == "Autitos") {
                                        Text("🏎️ Volantes en uso", color = WarningYellow, fontSize = 9.sp)
                                    }
                                }
                            }

                            matchingFinishedRecords.isNotEmpty() -> {
                                val rec = matchingFinishedRecords.first()
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🏁 FINALIZADA",
                                            color = Color(0xFFA0AEC0),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = rec.formatearRangoHorario().ifBlank { "${rec.parsearDuracionMinutos()}m" },
                                            color = Color(0xFFCBD5E0),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        text = "👤 ${rec.cliente.ifBlank { "Cliente" }}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (!rec.peliculaJuego.isNullOrBlank()) {
                                        Text(
                                            text = "🎬 ${rec.peliculaJuego}",
                                            color = TextSecondary,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else {
                                        Text(
                                            text = "💰 ${rec.totalCobrado} Bs",
                                            color = AccentLime.copy(alpha = 0.85f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            matchingReservations.isNotEmpty() -> {
                                val res = matchingReservations.first()
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "📅 RESERVA",
                                            color = Color(0xFF64B5F6),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "${res.horaProgramada} - ${res.calcularHoraFin()}",
                                            color = Color(0xFFBBDEFB),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        text = "👤 ${res.nombre}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        if (res.cantidadVolantes > 1 || res.tipoSala.contains("Auto", ignoreCase = true)) {
                                            Text("🏎️ ${res.cantidadVolantes} vol.", color = WarningYellow, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                        if (res.adelanto.isNotBlank()) {
                                            Text("💰 ${res.adelanto} Bs", color = AccentLime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                        else -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Libre", color = Color(0xFF555A70), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
}




/**
 * 📋 Vista de Tabla Resumen Detallada
 */
@Composable
private fun ScheduleTableView(
    rooms: List<Room>,
    reservations: List<Reservation>,
    records: List<SessionRecord> = emptyList(),
    onRoomClick: (Room) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Cabecera de la Tabla
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E2337), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("RECURSO / SALA", color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
            Text("ESTADO", color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("CLIENTE", color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f))
            Text("HORA INICIO", color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("HORA SALIDA / FIN", color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
            Text("VOLANTES / DETALLES", color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f))
            Text("COBRO / ADELANTO", color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f))
        }

        fun extractRoomNumber(name: String, id: Int): Int {
            val num = Regex("""\d+""").find(name)?.value?.toIntOrNull()
            return num ?: id
        }

        val occupiedRooms = rooms.filter { it.estado == RoomState.OCUPADA }
            .sortedWith(compareBy({ extractRoomNumber(it.nombre, it.id) }, { it.id }))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // 1. Sesiones Activas en Vivo
            if (occupiedRooms.isNotEmpty()) {
                item {
                    Text(
                        text = "🟢 SESIONES ACTIVAS EN VIVO (${occupiedRooms.size})",
                        color = AccentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                    )
                }

                items(occupiedRooms) { room ->
                    val icon = when (room.categoria) {
                        "Minicine" -> "🎬"
                        "Autitos" -> "🏎️"
                        else -> "💃"
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF14291D),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { onRoomClick(room) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$icon ${room.nombre}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
                            Text("🟢 En Vivo", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(room.cliente ?: "N/A", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1.4f))
                            Text(room.obtenerHoraInicioFormateada(), color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("🏁 ${room.obtenerHoraSalidaFormateada()} (${room.formatearTiempoRestante()})", color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                            Text(room.pelicula ?: if (room.categoria == "Autitos") "Volantes" else "N/A", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1.4f))
                            Text("${room.precioCobrado} Bs (${room.metodoPago})", color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f))
                        }
                    }
                }
            }

            // 2. Reservas Programadas
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "📅 RESERVAS PROGRAMADAS (${reservations.size})",
                    color = Color(0xFF64B5F6),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                )
            }

            if (reservations.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                        Text("No hay reservas programadas para la fecha seleccionada.", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            } else {
                items(reservations) { res ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF132233),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E88E5).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(res.tipoSala.ifBlank { "Sala General" }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
                            Text("📅 Reserva", color = Color(0xFF64B5F6), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${res.nombre} (${res.celular})", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1.4f))
                            Text(res.horaProgramada, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("🏁 ${res.calcularHoraFin()} (${res.duracion})", color = Color(0xFFBBDEFB), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                            val volanteTxt = if (res.cantidadVolantes > 1 || res.tipoSala.contains("Auto", ignoreCase = true)) "🏎️ ${res.cantidadVolantes} Volantes" else "Estándar"
                            Text(volanteTxt, color = WarningYellow, fontSize = 11.sp, modifier = Modifier.weight(1.4f))
                            Text("Adelanto: ${res.adelanto.ifBlank { "0" }} Bs", color = AccentLime, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f))
                        }
                    }
                }
            }

            // 3. Historial de Sesiones Finalizadas del Día
            if (records.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "🏁 SESIONES FINALIZADAS DEL DÍA (${records.size})",
                        color = Color(0xFFA0AEC0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                    )
                }

                items(records) { rec ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1A1F2C),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B4252)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(rec.nombreElemento.ifBlank { "Sala" }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
                            Text("🏁 Finalizada", color = Color(0xFFA0AEC0), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(rec.cliente.ifBlank { "Cliente" }, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1.4f))
                            Text(rec.formatearRangoHorario().substringBefore("-").trim().ifBlank { rec.fechaHora.takeLast(5) }, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("🏁 ${rec.formatearRangoHorario().substringAfter("-", "").trim().ifBlank { "${rec.parsearDuracionMinutos()} min" }}", color = Color(0xFFCBD5E0), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                            Text(rec.peliculaJuego?.ifBlank { "Sesión completada" } ?: "Sesión completada", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1.4f))
                            Text("${rec.totalCobrado} Bs (${rec.metodoPago})", color = AccentLime.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f))
                        }
                    }
                }
            }
        }
    }
}

private fun normalizeDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    val clean = dateStr.trim().replace("-", "/")
    val parts = clean.split("/")
    if (parts.size == 3) {
        if (parts[0].length == 4) {
            // yyyy/MM/dd -> dd/MM/yyyy
            val y = parts[0]
            val m = parts[1].padStart(2, '0')
            val d = parts[2].padStart(2, '0')
            return "$d/$m/$y"
        } else {
            // dd/MM/yyyy
            val d = parts[0].padStart(2, '0')
            val m = parts[1].padStart(2, '0')
            val y = if (parts[2].length == 2) "20" + parts[2] else parts[2]
            return "$d/$m/$y"
        }
    }
    return clean
}

private fun parseTimeToMinutes(timeStr: String?): Int? {
    if (timeStr.isNullOrBlank()) return null
    val clean = timeStr.trim().take(5)
    val parts = clean.split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: 0
    return h * 60 + m
}

private fun isRoomOccupiedInHourSlot(room: Room, hourInt: Int, isToday: Boolean): Boolean {
    if (!isToday || room.estado != RoomState.OCUPADA) return false

    val slotStartMin = hourInt * 60
    val slotEndMin = (hourInt + 1) * 60

    // 1. Determinar hora de fin en minutos (0..1439)
    val endMin: Int? = when {
        room.endTimeMillis != null && room.endTimeMillis > 0L -> {
            val cal = Calendar.getInstance().apply { timeInMillis = room.endTimeMillis }
            cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }
        !room.horaFinProgramada.isNullOrBlank() && room.horaFinProgramada != "Libre" -> {
            parseTimeToMinutes(room.horaFinProgramada)
        }
        else -> null
    }

    // 2. Determinar hora de inicio en minutos (0..1439)
    val startMin: Int? = when {
        room.endTimeMillis != null && room.tiempoRestanteMillis != null && room.tiempoRestanteMillis > 0L -> {
            val cal = Calendar.getInstance().apply { timeInMillis = room.endTimeMillis - room.tiempoRestanteMillis }
            cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }
        !room.horaReserva.isNullOrBlank() && room.horaReserva != "N/A" -> {
            parseTimeToMinutes(room.horaReserva)
        }
        endMin != null -> {
            maxOf(0, endMin - 60)
        }
        else -> null
    }

    if (startMin != null && endMin != null) {
        val (effStart, effEnd) = if (endMin < startMin) {
            startMin to (endMin + 1440)
        } else {
            startMin to endMin
        }
        val overlap = minOf(effEnd, slotEndMin) - maxOf(effStart, slotStartMin)
        return overlap > 2
    }

    if (room.horaFinProgramada == "Libre") {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hourInt == currentHour
    }

    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return hourInt == currentHour
}

private fun isReservationInHourSlot(res: Reservation, room: Room, allRooms: List<Room>, hourInt: Int): Boolean {
    // 1. Validar si la reserva corresponde a esta sala
    val resType = res.tipoSala.trim().lowercase()
    val roomName = room.nombre.trim().lowercase()

    val isAutitosReservation = resType.contains("auto") || 
                               resType.contains("volante") || 
                               resType.contains("car") || 
                               resType.contains("simulador") || 
                               res.cantidadVolantes > 1
    val isDanceReservation = resType.contains("dance")
    val isMinicineReservation = resType.contains("cine") || 
                                resType.contains("sala") || 
                                (!isAutitosReservation && !isDanceReservation)

    val matchesRoom = when {
        // Coincidencia de texto directa
        resType.isNotEmpty() && (resType == roomName || resType.contains(roomName) || roomName.contains(resType)) -> true

        // Reserva de Autitos / Volantes
        isAutitosReservation && room.categoria == "Autitos" -> {
            val resDigits = resType.filter { it.isDigit() }
            val roomDigits = roomName.filter { it.isDigit() }
            if (resDigits.isNotEmpty() && roomDigits.isNotEmpty()) {
                // Si la reserva especificó un número de Car / Volante particular (ej: "Car 3" o "Volante 2")
                resDigits == roomDigits || resDigits == (room.id % 100).toString() || resDigits == room.id.toString()
            } else {
                // Reserva general de autitos: asigna a los primeros N volantes según cantidadVolantes
                val autitosRooms = allRooms.filter { it.categoria == "Autitos" }.sortedBy { it.id }
                val carIndex = autitosRooms.indexOf(room)
                val requiredVolantes = if (res.cantidadVolantes > 0) res.cantidadVolantes else 1
                if (carIndex >= 0) carIndex < requiredVolantes else room.id in 101..(100 + requiredVolantes)
            }
        }

        // Reserva de Just Dance
        isDanceReservation && room.categoria == "Just Dance" -> {
            val resDigits = resType.filter { it.isDigit() }
            val roomDigits = roomName.filter { it.isDigit() }
            if (resDigits.isNotEmpty() && roomDigits.isNotEmpty()) {
                resDigits == roomDigits || resDigits == (room.id % 100).toString() || resDigits == room.id.toString()
            } else {
                val danceRooms = allRooms.filter { it.categoria == "Just Dance" }.sortedBy { it.id }
                val danceIndex = danceRooms.indexOf(room)
                if (danceIndex >= 0) danceIndex == 0 else room.id >= 200
            }
        }

        // Reserva de Minicine
        isMinicineReservation && room.categoria == "Minicine" -> {
            val resDigits = resType.filter { it.isDigit() }
            val roomDigits = roomName.filter { it.isDigit() }
            if (resDigits.isNotEmpty() && roomDigits.isNotEmpty()) {
                resDigits == roomDigits || resDigits == (room.id % 100).toString() || resDigits == room.id.toString()
            } else {
                val cineRooms = allRooms.filter { it.categoria == "Minicine" }.sortedBy { it.id }
                val cineIndex = cineRooms.indexOf(room)
                if (cineIndex >= 0) cineIndex == 0 else room.id == 1
            }
        }

        else -> false
    }

    if (!matchesRoom) return false

    // 2. Validar si el horario de la reserva solapa con este hourInt
    val startMin = parseTimeToMinutes(res.horaProgramada) ?: return false
    val durationMin = res.parseDurationMinutes()
    val endMin = startMin + durationMin

    val slotStartMin = hourInt * 60
    val slotEndMin = (hourInt + 1) * 60

    val overlap = minOf(endMin, slotEndMin) - maxOf(startMin, slotStartMin)
    return overlap > 2
}

private fun isRecordInHourSlot(rec: SessionRecord, room: Room, allRooms: List<Room>, hourInt: Int): Boolean {
    // 1. Validar si el registro corresponde a esta sala
    val recRoom = rec.nombreElemento.trim().lowercase()
    val roomName = room.nombre.trim().lowercase()

    val isAutitosRecord = recRoom.contains("auto") || 
                          recRoom.contains("volante") || 
                          recRoom.contains("car") || 
                          recRoom.contains("simulador") || 
                          (rec.cantidadVolantes != null && rec.cantidadVolantes > 1)
    val isDanceRecord = recRoom.contains("dance")
    val isMinicineRecord = recRoom.contains("cine") || 
                           recRoom.contains("sala") || 
                           (!isAutitosRecord && !isDanceRecord)

    val matchesRoom = when {
        recRoom.isNotEmpty() && (recRoom == roomName || recRoom.contains(roomName) || roomName.contains(recRoom)) -> true

        isAutitosRecord && room.categoria == "Autitos" -> {
            val recDigits = recRoom.filter { it.isDigit() }
            val roomDigits = roomName.filter { it.isDigit() }
            if (recDigits.isNotEmpty() && roomDigits.isNotEmpty()) {
                recDigits == roomDigits || recDigits == (room.id % 100).toString() || recDigits == room.id.toString()
            } else {
                val autitosRooms = allRooms.filter { it.categoria == "Autitos" }.sortedBy { it.id }
                val carIndex = autitosRooms.indexOf(room)
                val requiredVolantes = rec.cantidadVolantes ?: 1
                if (carIndex >= 0) carIndex < requiredVolantes else room.id in 101..(100 + requiredVolantes)
            }
        }

        isDanceRecord && room.categoria == "Just Dance" -> {
            val recDigits = recRoom.filter { it.isDigit() }
            val roomDigits = roomName.filter { it.isDigit() }
            if (recDigits.isNotEmpty() && roomDigits.isNotEmpty()) {
                recDigits == roomDigits || recDigits == (room.id % 100).toString() || recDigits == room.id.toString()
            } else {
                val danceRooms = allRooms.filter { it.categoria == "Just Dance" }.sortedBy { it.id }
                val danceIndex = danceRooms.indexOf(room)
                if (danceIndex >= 0) danceIndex == 0 else room.id >= 200
            }
        }

        isMinicineRecord && room.categoria == "Minicine" -> {
            val recDigits = recRoom.filter { it.isDigit() }
            val roomDigits = roomName.filter { it.isDigit() }
            if (recDigits.isNotEmpty() && roomDigits.isNotEmpty()) {
                recDigits == roomDigits || recDigits == (room.id % 100).toString() || recDigits == room.id.toString()
            } else {
                val cineRooms = allRooms.filter { it.categoria == "Minicine" }.sortedBy { it.id }
                val cineIndex = cineRooms.indexOf(room)
                if (cineIndex >= 0) cineIndex == 0 else room.id == 1
            }
        }

        else -> false
    }

    if (!matchesRoom) return false

    // 2. Validar solapamiento horario
    val startMin = rec.extraerHoraInicioMinutos() ?: return false
    val durationMin = rec.parsearDuracionMinutos()
    val endMin = startMin + durationMin

    val slotStartMin = hourInt * 60
    val slotEndMin = (hourInt + 1) * 60

    val overlap = minOf(endMin, slotEndMin) - maxOf(startMin, slotStartMin)
    return overlap > 2
}


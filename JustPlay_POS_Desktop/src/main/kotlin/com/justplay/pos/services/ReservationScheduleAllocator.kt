package com.justplay.pos.services

import com.justplay.pos.core.models.Reservation
import com.justplay.pos.core.models.Room
import com.justplay.pos.core.models.RoomState
import com.justplay.pos.core.models.SessionRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 🎯 Servicio de Asignación y Distribución Secuencial de Reservas (SOLID).
 *
 * Cumple con el requerimiento:
 * "Mostrar todas las reservas de forma ordenada, empezando por la sala 1 y continuando
 * secuencialmente con las siguientes si están ocupadas."
 */
object ReservationScheduleAllocator {

    private const val BUFFER_MINUTES = 15 // Margen de desinfección/limpieza entre funciones

    data class BusyInterval(val startMin: Int, val endMin: Int)

    /**
     * Extrae el número de sala (ej. "Sala 1" -> 1, "Sala 10" -> 10, "Car 3" -> 3).
     */
    fun extractRoomNumber(name: String, id: Int): Int {
        val num = Regex("""\d+""").find(name)?.value?.toIntOrNull()
        return num ?: (id % 100)
    }

    /**
     * Normaliza formato de fecha (dd/MM/yyyy).
     */
    fun normalizeDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        val clean = dateStr.trim().replace("-", "/")
        val parts = clean.split("/")
        if (parts.size == 3) {
            if (parts[0].length == 4) {
                val y = parts[0]
                val m = parts[1].padStart(2, '0')
                val d = parts[2].padStart(2, '0')
                return "$d/$m/$y"
            } else {
                val d = parts[0].padStart(2, '0')
                val m = parts[1].padStart(2, '0')
                val y = if (parts[2].length == 2) "20" + parts[2] else parts[2]
                return "$d/$m/$y"
            }
        }
        return clean
    }

    /**
     * Convierte hora "HH:mm" a minutos del día (0..1439).
     */
    fun parseTimeToMinutes(timeStr: String?): Int? {
        if (timeStr.isNullOrBlank()) return null
        val clean = timeStr.trim().take(5)
        val parts = clean.split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: 0
        return h * 60 + m
    }

    /**
     * Determina la categoría a la que corresponde la reserva.
     */
    fun getReservationCategory(res: Reservation): String {
        val resType = res.tipoSala.trim().lowercase()
        val isAutitos = resType.contains("aut") ||
                        resType.contains("volante") ||
                        resType.contains("car") ||
                        resType.contains("simulador") ||
                        res.cantidadVolantes > 1
        val isDance = resType.contains("dance")
        return when {
            isDance -> "Just Dance"
            isAutitos -> "Autitos"
            else -> "Minicine"
        }
    }

    /**
     * Comprueba si un registro pertenece a una sala específica.
     */
    fun recordMatchesRoom(rec: SessionRecord, room: Room): Boolean {
        val recRoom = rec.nombreElemento.trim().lowercase()
        val roomName = room.nombre.trim().lowercase()
        if (recRoom.isNotEmpty() && (recRoom == roomName || recRoom.contains(roomName) || roomName.contains(recRoom))) {
            return true
        }
        val recDigits = recRoom.filter { it.isDigit() }
        val roomDigits = roomName.filter { it.isDigit() }
        if (recDigits.isNotEmpty() && roomDigits.isNotEmpty()) {
            return recDigits == roomDigits || recDigits == (room.id % 100).toString() || recDigits == room.id.toString()
        }
        return false
    }

    /**
     * Calcula la hora de inicio de una sala en minutos.
     */
    fun extractRoomStartMinutes(room: Room): Int? {
        val endMin = extractRoomEndMinutes(room)
        return when {
            room.endTimeMillis != null && room.tiempoRestanteMillis != null && room.tiempoRestanteMillis > 0L -> {
                val cal = Calendar.getInstance().apply { timeInMillis = room.endTimeMillis - room.tiempoRestanteMillis }
                cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            }
            !room.horaReserva.isNullOrBlank() && room.horaReserva != "N/A" -> {
                parseTimeToMinutes(room.horaReserva)
            }
            endMin != null -> maxOf(0, endMin - 60)
            else -> null
        }
    }

    /**
     * Calcula la hora de fin de una sala en minutos.
     */
    fun extractRoomEndMinutes(room: Room): Int? {
        return when {
            room.endTimeMillis != null && room.endTimeMillis > 0L -> {
                val cal = Calendar.getInstance().apply { timeInMillis = room.endTimeMillis }
                cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            }
            !room.horaFinProgramada.isNullOrBlank() && room.horaFinProgramada != "Libre" -> {
                parseTimeToMinutes(room.horaFinProgramada)
            }
            else -> null
        }
    }

    /**
     * Asigna secuencialmente las reservas a las salas disponibles.
     * Retorna un mapa: [idFirebase -> List<RoomId>]
     */
    fun computeAssignments(
        rooms: List<Room>,
        reservations: List<Reservation>,
        records: List<SessionRecord>,
        isToday: Boolean
    ): Map<String, List<Int>> {
        if (rooms.isEmpty() || reservations.isEmpty()) return emptyMap()

        val assignmentMap = mutableMapOf<String, List<Int>>()
        val todayNorm = normalizeDate(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
        val categories = listOf("Minicine", "Autitos", "Just Dance")

        for (category in categories) {
            val categoryRooms = rooms.filter { it.categoria == category }
                .sortedWith(compareBy({ extractRoomNumber(it.nombre, it.id) }, { it.id }))
            if (categoryRooms.isEmpty()) continue

            val catReservations = reservations.filter { getReservationCategory(it) == category }
            if (catReservations.isEmpty()) continue

            // Agrupar por fecha
            val byDate = catReservations.groupBy { normalizeDate(it.fecha) }

            for ((dateNorm, dateResList) in byDate) {
                val isGroupToday = isToday && (dateNorm.isBlank() || dateNorm == todayNorm)

                val roomBusyMap = mutableMapOf<Int, MutableList<BusyInterval>>()
                categoryRooms.forEach { roomBusyMap[it.id] = mutableListOf() }

                // 1. Ocupación por sesiones en vivo (si es hoy)
                if (isGroupToday) {
                    for (room in categoryRooms) {
                        if (room.estado == RoomState.OCUPADA) {
                            val sMin = extractRoomStartMinutes(room)
                            val eMin = extractRoomEndMinutes(room)
                            if (sMin != null && eMin != null) {
                                roomBusyMap[room.id]?.add(BusyInterval(sMin, eMin + BUFFER_MINUTES))
                            }
                        }
                    }
                }

                // 2. Ocupación por registros finalizados del día
                for (rec in records) {
                    val recNorm = normalizeDate(rec.extraerFecha())
                    if (dateNorm.isNotBlank() && recNorm.isNotBlank() && recNorm != dateNorm) continue

                    val sMin = rec.extraerHoraInicioMinutos()
                    if (sMin != null) {
                        val eMin = sMin + rec.parsearDuracionMinutos()
                        for (room in categoryRooms) {
                            if (recordMatchesRoom(rec, room)) {
                                roomBusyMap[room.id]?.add(BusyInterval(sMin, eMin + BUFFER_MINUTES))
                            }
                        }
                    }
                }

                // 3. Separar asignaciones explícitas (ej. "Sala 3", "Car 2") de asignaciones generales
                val (explicitList, generalList) = dateResList.partition { res ->
                    val digits = res.tipoSala.filter { it.isDigit() }
                    digits.isNotEmpty() && categoryRooms.any { r ->
                        val rDigits = r.nombre.filter { it.isDigit() }
                        digits == rDigits || digits == (r.id % 100).toString() || digits == r.id.toString()
                    }
                }

                // Procesar reservas explícitas primero
                for (res in explicitList) {
                    val digits = res.tipoSala.filter { it.isDigit() }
                    val targetRoom = categoryRooms.firstOrNull { r ->
                        val rDigits = r.nombre.filter { it.isDigit() }
                        digits == rDigits || digits == (r.id % 100).toString() || digits == r.id.toString()
                    }
                    if (targetRoom != null) {
                        assignmentMap[res.idFirebase] = listOf(targetRoom.id)
                        val sMin = parseTimeToMinutes(res.horaProgramada) ?: 0
                        val eMin = sMin + res.parseDurationMinutes()
                        roomBusyMap[targetRoom.id]?.add(BusyInterval(sMin, eMin + BUFFER_MINUTES))
                    }
                }

                // 4. Procesar reservas generales ordenadas por hora
                val sortedGeneral = generalList.sortedWith(
                    compareBy(
                        { parseTimeToMinutes(it.horaProgramada) ?: 0 },
                        { it.idFirebase }
                    )
                )

                for (res in sortedGeneral) {
                    val sMin = parseTimeToMinutes(res.horaProgramada) ?: 600
                    val durMin = res.parseDurationMinutes()
                    val eMin = sMin + durMin
                    val neededVolantes = if (category == "Autitos") {
                        if (res.cantidadVolantes > 0) res.cantidadVolantes else 1
                    } else 1

                    fun hasConflict(roomId: Int): Boolean {
                        val intervals = roomBusyMap[roomId] ?: return false
                        return intervals.any { busy ->
                            val overlap = minOf(eMin, busy.endMin) - maxOf(sMin, busy.startMin)
                            overlap > 0
                        }
                    }

                    if (neededVolantes > 1 && category == "Autitos") {
                        val available = categoryRooms.filter { !hasConflict(it.id) }
                        val chosen = if (available.size >= neededVolantes) {
                            available.take(neededVolantes)
                        } else {
                            val needed = neededVolantes - available.size
                            val remaining = categoryRooms.filter { it !in available }
                            available + remaining.take(needed)
                        }
                        assignmentMap[res.idFirebase] = chosen.map { it.id }
                        chosen.forEach { r ->
                            roomBusyMap[r.id]?.add(BusyInterval(sMin, eMin + BUFFER_MINUTES))
                        }
                    } else {
                        // Buscar secuencialmente empezando por la primera sala disponible
                        val freeRoom = categoryRooms.firstOrNull { !hasConflict(it.id) }
                        val chosenRoom = freeRoom ?: categoryRooms.minByOrNull { r ->
                            val intervals = roomBusyMap[r.id] ?: emptyList()
                            intervals.sumOf { busy ->
                                maxOf(0, minOf(eMin, busy.endMin) - maxOf(sMin, busy.startMin))
                            }
                        } ?: categoryRooms.first()

                        assignmentMap[res.idFirebase] = listOf(chosenRoom.id)
                        roomBusyMap[chosenRoom.id]?.add(BusyInterval(sMin, eMin + BUFFER_MINUTES))
                    }
                }
            }
        }

        return assignmentMap
    }
}

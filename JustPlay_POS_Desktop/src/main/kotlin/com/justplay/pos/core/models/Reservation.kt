package com.justplay.pos.core.models

import java.util.UUID

data class Reservation(
    val idFirebase: String = UUID.randomUUID().toString(),
    val nombre: String = "",
    val celular: String = "",
    val fecha: String = "",
    val horaProgramada: String = "",
    val tipoSala: String = "",
    val adelanto: String = "",
    val metodoPagoAdelanto: String = "Efectivo",
    val comprobanteUri: String? = null,
    val duracion: String = "1:00",
    val cantidadVolantes: Int = 1,
    val asistio: Boolean = false,
    val horaLlegada: String? = null
) {
    fun parseDurationMinutes(): Int {
        val clean = duracion.trim().lowercase()
        if (clean.isEmpty() || clean == "libre" || clean == "0") return 60
        if (clean.contains(":")) {
            val parts = clean.split(":")
            val hours = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
            val minutes = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            return (hours * 60 + minutes).coerceAtLeast(15)
        }
        return (clean.filter { it.isDigit() }.toIntOrNull() ?: 60).coerceAtLeast(15)
    }

    fun calcularHoraFin(): String {
        return try {
            val cleanHora = horaProgramada.trim().take(5)
            val parts = cleanHora.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: return cleanHora
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val totalMin = h * 60 + m + parseDurationMinutes()
            val endH = (totalMin / 60) % 24
            val endM = totalMin % 60
            String.format("%02d:%02d", endH, endM)
        } catch (_: Exception) {
            horaProgramada
        }
    }
}

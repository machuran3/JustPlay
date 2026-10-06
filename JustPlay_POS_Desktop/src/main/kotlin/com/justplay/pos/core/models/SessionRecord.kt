package com.justplay.pos.core.models

/**
 * Representa una sesión o alquiler de sala/volante/dance registrado en /registros de Firebase.
 * Permite mantener visibles las sesiones finalizadas en el Tablero de Horarios hasta que se eliminen del registro.
 */
data class SessionRecord(
    val idFirebase: String = "",
    val tipo: String = "",
    val nombreElemento: String = "",
    val cliente: String = "",
    val celular: String? = null,
    val peliculaJuego: String? = null,
    val tiempoConfigurado: String? = null,
    val fechaHora: String = "",
    val timestamp: Long = 0L,
    val pedidos: String = "",
    val totalCobrado: Double = 0.0,
    val metodoPago: String = "Efectivo",
    val comprobanteUri: String? = null,
    val cantidadVolantes: Int? = null,
    val eliminado: Boolean = false
) {
    /**
     * Extrae la fecha en formato dd/MM/yyyy.
     */
    fun extraerFecha(): String {
        val clean = fechaHora.trim()
        val parts = clean.split(" ")
        return parts.getOrNull(0) ?: ""
    }

    /**
     * Extrae la hora de inicio en minutos del día (0..1439).
     */
    fun extraerHoraInicioMinutos(): Int? {
        val clean = fechaHora.trim()
        val parts = clean.split(" ")
        val timePart = if (parts.size >= 2) parts[1] else return null
        val timeSplit = timePart.split(":")
        val h = timeSplit.getOrNull(0)?.toIntOrNull() ?: return null
        val m = timeSplit.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    /**
     * Parsea la duración configurada en minutos.
     */
    fun parsearDuracionMinutos(): Int {
        val clean = (tiempoConfigurado ?: "").trim().lowercase()
        if (clean.isEmpty() || clean == "libre" || clean == "continuo" || clean == "0") return 60
        if (clean.contains(":")) {
            val parts = clean.split(":")
            val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            return (h * 60 + m).coerceAtLeast(15)
        }
        val digits = clean.filter { it.isDigit() }.toIntOrNull() ?: 60
        return digits.coerceAtLeast(15)
    }

    /**
     * Formatea el rango de hora (HH:mm - HH:mm).
     */
    fun formatearRangoHorario(): String {
        val startMin = extraerHoraInicioMinutos() ?: return fechaHora.takeLast(5)
        val endMin = (startMin + parsearDuracionMinutos()) % 1440
        val startStr = String.format("%02d:%02d", startMin / 60, startMin % 60)
        val endStr = String.format("%02d:%02d", endMin / 60, endMin % 60)
        return "$startStr - $endStr"
    }
}

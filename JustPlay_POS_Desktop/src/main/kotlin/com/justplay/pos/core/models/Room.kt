package com.justplay.pos.core.models

/**
 * Estados de ocupación de una sala.
 */
enum class RoomState {
    DISPONIBLE,
    RESERVADA,
    OCUPADA
}

/**
 * Pedido / Consumo asignado a una sala específica.
 */
data class RoomOrder(
    val idFirebase: String = "",
    val cantidad: Int = 1,
    val nombre: String = "",
    val precioUnidad: Double = 0.0,
    val estaEntregado: Boolean = true
) {
    val subtotal: Double get() = cantidad * precioUnidad
}

/**
 * Modelo de datos de Sala en el ecosistema JustPlay (Minicine, Autitos, Just Dance).
 */
data class Room(
    val id: Int = 0,
    val idFirebase: String = "",
    val nombre: String = "",
    val estado: RoomState = RoomState.DISPONIBLE,
    val horaReserva: String? = null,
    val cliente: String? = null,
    val celular: String? = null,
    val pelicula: String? = null,
    val tiempoRestanteMillis: Long? = null,
    val horaFinProgramada: String? = null,
    val endTimeMillis: Long? = null,
    val precioCobrado: Double = 0.0,
    val metodoPago: String = "Efectivo",
    val montoEfectivo: Double = 0.0,
    val montoQR: Double = 0.0,
    val comprobanteUri: String? = null,
    val pedidos: List<RoomOrder> = emptyList()
) {
    val categoria: String
        get() = when {
            nombre.contains("Dance", ignoreCase = true) || id >= 200 -> "Just Dance"
            nombre.contains("Car", ignoreCase = true) || nombre.contains("Auto", ignoreCase = true) || nombre.contains("Volante", ignoreCase = true) || id in 101..199 -> "Autitos"
            else -> "Minicine"
        }

    val precioBaseSugerido: Double
        get() = when (categoria) {
            "Minicine" -> BasePriceConfig.precioMinicine
            "Autitos" -> BasePriceConfig.precioVolantes
            "Just Dance" -> BasePriceConfig.precioJustDance
            else -> if (id < 100) BasePriceConfig.precioMinicine else BasePriceConfig.precioVolantes
        }

    val totalPedidos: Double get() = pedidos.sumOf { it.subtotal }
    val totalGeneral: Double get() = precioCobrado + totalPedidos

    fun getEfectivoAmount(): Double {
        val cleanMetodo = metodoPago.trim().lowercase()
        // 1. Si es puramente QR, Efectivo es estrictamente 0.0
        if (cleanMetodo == "qr" || (cleanMetodo.contains("qr") && !cleanMetodo.contains("mixto") && !cleanMetodo.contains("efec"))) {
            return 0.0
        }
        // 2. Si es Mixto, calcular porción de efectivo
        if (cleanMetodo.startsWith("mixto") || cleanMetodo.contains("mixto")) {
            if (montoEfectivo > 0.0) return montoEfectivo
            val regex1 = Regex("""(?i)(?:efec|ef|💵)\s*:?\s*([\d.]+)""")
            val match1 = regex1.find(metodoPago)
            if (match1 != null) {
                return match1.groupValues[1].toDoubleOrNull() ?: (totalGeneral / 2)
            }
            val regex2 = Regex("""(?i)([\d.]+)\s*(?:efec|ef|💵|bs)""")
            val match2 = regex2.find(metodoPago)
            if (match2 != null) {
                return match2.groupValues[1].toDoubleOrNull() ?: (totalGeneral / 2)
            }
            return totalGeneral / 2
        }
        // 3. Si es Efectivo o cualquier otro, todo es Efectivo
        if (montoEfectivo > 0.0) return montoEfectivo
        return totalGeneral
    }

    fun getQRAmount(): Double {
        val cleanMetodo = metodoPago.trim().lowercase()
        // 1. Si es puramente Efectivo, QR es estrictamente 0.0
        if (cleanMetodo == "efectivo" || (!cleanMetodo.contains("qr") && !cleanMetodo.contains("mixto"))) {
            return 0.0
        }
        // 2. Si es Mixto, calcular porción de QR
        if (cleanMetodo.startsWith("mixto") || cleanMetodo.contains("mixto")) {
            if (montoQR > 0.0) return montoQR
            val regex1 = Regex("""(?i)(?:qr|📱)\s*:?\s*([\d.]+)""")
            val match1 = regex1.find(metodoPago)
            if (match1 != null) {
                return match1.groupValues[1].toDoubleOrNull() ?: maxOf(0.0, totalGeneral - getEfectivoAmount())
            }
            val regex2 = Regex("""(?i)([\d.]+)\s*(?:qr|📱)""")
            val match2 = regex2.find(metodoPago)
            if (match2 != null) {
                return match2.groupValues[1].toDoubleOrNull() ?: maxOf(0.0, totalGeneral - getEfectivoAmount())
            }
            return maxOf(0.0, totalGeneral - getEfectivoAmount())
        }
        // 3. Si es QR puro, todo es QR
        if (montoQR > 0.0) return montoQR
        return totalGeneral
    }

    /**
     * Calcula el tiempo restante en milisegundos a partir de endTimeMillis.
     */
    fun calcularTiempoRestanteMillis(): Long {
        val end = endTimeMillis ?: return 0L
        val diff = end - System.currentTimeMillis()
        return if (diff > 0) diff else 0L
    }

    /**
     * Formatea el tiempo restante en mm:ss o hh:mm:ss.
     */
    fun formatearTiempoRestante(): String {
        if (estado != RoomState.OCUPADA) return "Disponible"
        val millis = calcularTiempoRestanteMillis()
        if (millis <= 0 && (endTimeMillis ?: 0L) > 0L) return "⏰ ¡Tiempo Finalizado!"
        if (horaFinProgramada == "Libre" || (endTimeMillis == null && horaFinProgramada.isNullOrBlank())) return "Tiempo Libre"

        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    /**
     * Retorna la hora de salida / finalización de la sala formateada (HH:mm).
     */
    fun obtenerHoraSalidaFormateada(): String {
        if (estado != RoomState.OCUPADA) return "N/A"
        if (horaFinProgramada == "Libre" || (endTimeMillis == null && horaFinProgramada.isNullOrBlank())) {
            return "Tiempo Libre"
        }
        if (!horaFinProgramada.isNullOrBlank() && horaFinProgramada != "Libre") {
            return horaFinProgramada
        }
        val end = endTimeMillis ?: return "N/A"
        return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(end))
    }

    /**
     * Retorna la hora de inicio de la sesión.
     */
    fun obtenerHoraInicioFormateada(): String {
        return horaReserva?.ifBlank { "N/A" } ?: "N/A"
    }
}

object BasePriceConfig {
    var precioMinicine: Double = 40.0
    var precioVolantes: Double = 15.0
    var precioJustDance: Double = 15.0

    fun updateFromMap(prices: Map<String, Double>) {
        prices["precio_minicine"]?.let { if (it > 0) precioMinicine = it }
        prices["precio_volantes"]?.let { if (it > 0) precioVolantes = it }
        prices["precio_just_dance"]?.let { if (it > 0) precioJustDance = it }
    }
}

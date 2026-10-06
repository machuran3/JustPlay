package com.example.adminjustplay

import java.util.UUID

data class Reserva(
    var idFirebase: String = UUID.randomUUID().toString(),
    var nombre: String = "",
    var celular: String = "",
    var fecha: String = "",
    var horaProgramada: String = "",
    var tipoSala: String = "",
    var adelanto: String = "",
    var metodoPagoAdelanto: String = "Efectivo",
    var comprobanteUri: String? = null,
    var duracion: String = "",
    var cantidadVolantes: Int = 1,
    var asistio: Boolean = false,
    var horaLlegada: String? = null
) {
    fun getSortTimestamp(): Long {
        return try {
            val sdf = if (fecha.contains("-")) {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            } else {
                java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            }
            val cleanHora = horaProgramada.trim().take(5)
            sdf.parse("${fecha.trim()} $cleanHora")?.time ?: Long.MAX_VALUE
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }
}

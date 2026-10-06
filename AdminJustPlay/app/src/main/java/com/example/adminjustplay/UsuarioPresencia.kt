package com.example.adminjustplay

data class UsuarioPresencia(
    var id: String = "",
    var nombre: String = "",
    var rol: String = "Empleado", // "Administrador" o "Empleado"
    var app: String = "Employ_JustPlay",
    var dispositivo: String = "",
    var enLinea: Boolean = false,
    var ultimaConexion: Long = 0L,
    var ultimaConexionTexto: String = "",
    var seccionActual: String = "Tablero",
    var bloqueado: Boolean = false,
    var motivoBloqueo: String = "Acceso restringido por el Administrador",
    var pin: String = "0001",
    var tiempoLimiteMillis: Long = 0L, // 0 = Indefinido
    var tipoTiempoAcceso: String = "Indefinido", // "1 Hora", "2 Horas", "8 Horas (Turno)", "1 Día", "Indefinido"
    var fechaFinAccesoTexto: String = "Sin límite"
) {
    fun estaExpirado(): Boolean {
        return tiempoLimiteMillis > 0L && System.currentTimeMillis() > tiempoLimiteMillis
    }

    fun obtenerTiempoRestanteTexto(): String {
        if (tiempoLimiteMillis <= 0L) return "♾️ Permanente (Hasta desactivar)"
        val diff = tiempoLimiteMillis - System.currentTimeMillis()
        if (diff <= 0L) return "⏰ ACCESO EXPIRADO"
        val horas = diff / (1000 * 60 * 60)
        val minutos = (diff / (1000 * 60)) % 60
        return if (horas > 24) {
            val dias = horas / 24
            "⏳ Quedan ${dias}d ${horas % 24}h (Hasta $fechaFinAccesoTexto)"
        } else if (horas > 0) {
            "⏳ Quedan ${horas}h ${minutos}m (Hasta $fechaFinAccesoTexto)"
        } else {
            "⏳ Quedan ${minutos}m (Hasta $fechaFinAccesoTexto)"
        }
    }
}

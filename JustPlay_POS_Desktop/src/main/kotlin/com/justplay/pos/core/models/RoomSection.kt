package com.justplay.pos.core.models

data class RoomSection(
    val id: String = "",
    val nombre: String = "",
    val icono: String = "🎮",
    val orden: Int = 0,
    val precioBaseDefault: Double = 15.0,
    val tipoCobro: String = "TIEMPO_LIBRE", // "POR_TARIFA", "TIEMPO_LIBRE", "POR_HORA"
    val permitePelicula: Boolean = false,
    val prefijoSala: String = "Sala",
    val baseIdRango: Int = 300,
    val activa: Boolean = true
)

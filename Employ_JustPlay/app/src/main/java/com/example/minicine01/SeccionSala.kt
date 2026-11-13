package com.example.minicine01

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class SeccionSala(
    var id: String = "",
    var nombre: String = "",
    var icono: String = "🎮",
    var orden: Int = 0,
    var precioBaseDefault: Double = 15.0,
    var tipoCobro: String = "TIEMPO_LIBRE", // "POR_TARIFA", "TIEMPO_LIBRE", "POR_HORA"
    var permitePelicula: Boolean = false,
    var prefijoSala: String = "Sala",
    var baseIdRango: Int = 300,
    var activa: Boolean = true
)

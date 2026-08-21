package com.example.minicine01

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
)
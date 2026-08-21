package com.example.minicine01

enum class EstadoSala { DISPONIBLE, RESERVADA, OCUPADA }

data class Pedido(
    var idFirebase: String = "",
    var cantidad: Int = 0,
    var nombre: String = "",
    var precioUnidad: Double = 0.0,
    var estaEntregado: Boolean = false
) {
    fun subtotal(): Double = cantidad * precioUnidad
}

data class Sala(
    var id: Int = 0,
    var nombre: String = "",
    var estado: EstadoSala = EstadoSala.DISPONIBLE,
    var horaReserva: String? = null,
    var cliente: String? = null,
    var celular: String? = null,
    var pelicula: String? = null,
    var tiempoRestanteMillis: Long? = null,
    var horaFinProgramada: String? = null,
    var endTimeMillis: Long? = null,
    var precioCobrado: Double = 0.0,
    var metodoPago: String = "Efectivo",
    var comprobanteUri: String? = null,
    var pedidos: MutableList<Pedido> = mutableListOf()
) {
    fun calcularTotalPedidos(): Double = pedidos.sumOf { it.subtotal() }
    fun calcularTotalGeneral(): Double = precioCobrado + calcularTotalPedidos()
}

data class RegistroEntry(
    var idFirebase: String = "",
    var tipo: String = "",
    var nombreElemento: String = "",
    var cliente: String = "",
    var celular: String? = null,
    var peliculaJuego: String? = null,
    var tiempoConfigurado: String? = null,
    var fechaHora: String = "",
    var pedidos: String = "",
    var totalCobrado: Double = 0.0,
    var metodoPago: String = "Efectivo",
    var comprobanteUri: String? = null,
    var cantidadVolantes: Int? = null
)

data class ClienteVetado(
    var celular: String = "",
    var nombre: String? = null,
    var motivo: String = ""
)
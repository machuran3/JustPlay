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
    var idFirebase: String = "",
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
    var montoEfectivo: Double = 0.0,
    var montoQR: Double = 0.0,
    var comprobanteUri: String? = null,
    var pedidos: MutableList<Pedido> = mutableListOf()
) {
    fun calcularTotalPedidos(): Double = pedidos.sumOf { it.subtotal() }
    fun calcularTotalGeneral(): Double = precioCobrado + calcularTotalPedidos()
    fun getEfectivoAmount(): Double {
        val cleanMetodo = metodoPago.trim().lowercase()
        val total = calcularTotalGeneral()
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
                return match1.groupValues[1].toDoubleOrNull() ?: (total / 2)
            }
            val regex2 = Regex("""(?i)([\d.]+)\s*(?:efec|ef|💵|bs)""")
            val match2 = regex2.find(metodoPago)
            if (match2 != null) {
                return match2.groupValues[1].toDoubleOrNull() ?: (total / 2)
            }
            return total / 2
        }
        // 3. Si es Efectivo o cualquier otro, todo es Efectivo
        if (montoEfectivo > 0.0) return montoEfectivo
        return total
    }

    fun getQRAmount(): Double {
        val cleanMetodo = metodoPago.trim().lowercase()
        val total = calcularTotalGeneral()
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
                return match1.groupValues[1].toDoubleOrNull() ?: maxOf(0.0, total - getEfectivoAmount())
            }
            val regex2 = Regex("""(?i)([\d.]+)\s*(?:qr|📱)""")
            val match2 = regex2.find(metodoPago)
            if (match2 != null) {
                return match2.groupValues[1].toDoubleOrNull() ?: maxOf(0.0, total - getEfectivoAmount())
            }
            return maxOf(0.0, total - getEfectivoAmount())
        }
        // 3. Si es QR puro, todo es QR
        if (montoQR > 0.0) return montoQR
        return total
    }

    fun getPrecioBaseSugerido(): Double {
        return when {
            nombre.contains("Dance", ignoreCase = true) || id >= 200 -> BasePriceConfig.precioJustDance
            nombre.contains("Car", ignoreCase = true) || nombre.contains("Auto", ignoreCase = true) || nombre.contains("Volante", ignoreCase = true) || id in 101..199 -> BasePriceConfig.precioVolantes
            else -> BasePriceConfig.precioMinicine
        }
    }
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
    var timestamp: Long = 0L,
    var pedidos: String = "",
    var totalCobrado: Double = 0.0,
    var metodoPago: String = "Efectivo",
    var montoEfectivo: Double = 0.0,
    var montoQR: Double = 0.0,
    var comprobanteUri: String? = null,
    var cantidadVolantes: Int? = null,
    var eliminado: Boolean = false,
    var fechaEliminacion: String? = null,
    var eliminadoPor: String? = null,
    var creadoPor: String? = null
) {
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
                return match1.groupValues[1].toDoubleOrNull() ?: (totalCobrado / 2)
            }
            val regex2 = Regex("""(?i)([\d.]+)\s*(?:efec|ef|💵|bs)""")
            val match2 = regex2.find(metodoPago)
            if (match2 != null) {
                return match2.groupValues[1].toDoubleOrNull() ?: (totalCobrado / 2)
            }
            return totalCobrado / 2
        }
        // 3. Si es Efectivo o cualquier otro, todo es Efectivo
        if (montoEfectivo > 0.0) return montoEfectivo
        return totalCobrado
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
                return match1.groupValues[1].toDoubleOrNull() ?: maxOf(0.0, totalCobrado - getEfectivoAmount())
            }
            val regex2 = Regex("""(?i)([\d.]+)\s*(?:qr|📱)""")
            val match2 = regex2.find(metodoPago)
            if (match2 != null) {
                return match2.groupValues[1].toDoubleOrNull() ?: maxOf(0.0, totalCobrado - getEfectivoAmount())
            }
            return maxOf(0.0, totalCobrado - getEfectivoAmount())
        }
        // 3. Si es QR puro, todo es QR
        if (montoQR > 0.0) return montoQR
        return totalCobrado
    }
}

data class ClienteVetado(
    var celular: String = "",
    var nombre: String? = null,
    var motivo: String = ""
)

object BasePriceConfig {
    var precioMinicine: Double = 35.0
    var precioVolantes: Double = 15.0
    var precioJustDance: Double = 15.0

    fun updateFromSnapshot(snapshot: com.google.firebase.database.DataSnapshot) {
        val pM = snapshot.child("precio_minicine").getValue(Double::class.java)
            ?: snapshot.child("precio_minicine").getValue(Long::class.java)?.toDouble()
        val pV = snapshot.child("precio_volantes").getValue(Double::class.java)
            ?: snapshot.child("precio_volantes").getValue(Long::class.java)?.toDouble()
        val pJD = snapshot.child("precio_just_dance").getValue(Double::class.java)
            ?: snapshot.child("precio_just_dance").getValue(Long::class.java)?.toDouble()

        if (pM != null && pM > 0) precioMinicine = pM
        if (pV != null && pV > 0) precioVolantes = pV
        if (pJD != null && pJD > 0) precioJustDance = pJD
    }
}
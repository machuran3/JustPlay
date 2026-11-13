package com.justplay.pos.core.utils

data class FilaTarifaMinicine(
    val duracionTexto: String,
    val tiempoMinutosTexto: String,
    val minutos: Int,
    val tiempoExtraTexto: String,
    val calculoTexto: String,
    val totalBs: Double
)

object MinicineTarifaCalculator {

    val TABLA_OFICIAL: List<FilaTarifaMinicine>
        get() {
            val base = com.justplay.pos.core.models.BasePriceConfig.precioMinicine
            val baseInt = if (base % 1.0 == 0.0) base.toInt().toString() else base.toString()
            return listOf(
                FilaTarifaMinicine("Hasta 2 horas", "0 a 120 min", 120, "Ninguno", "Bs. $baseInt (Base)", base),
                FilaTarifaMinicine("2h 5 min", "125 min", 125, "5 min", "Bs. $baseInt + Bs. 5", base + 5.0),
                FilaTarifaMinicine("2h 15 min", "135 min", 135, "15 min", "Bs. $baseInt + Bs. 10", base + 10.0),
                FilaTarifaMinicine("2h 30 min", "150 min", 150, "30 min", "Bs. $baseInt + Bs. 15", base + 15.0),
                FilaTarifaMinicine("2h 45 min", "165 min", 165, "45 min", "Bs. $baseInt + Bs. 20", base + 20.0),
                FilaTarifaMinicine("3 horas exactas", "180 min", 180, "60 min (1 hora)", "Bs. $baseInt + Bs. 30", base + 30.0),
                FilaTarifaMinicine("3h 15 min", "195 min", 195, "75 min", "Bs. $baseInt + Bs. 35", base + 35.0),
                FilaTarifaMinicine("3h 30 min", "210 min", 210, "90 min", "Bs. $baseInt + Bs. 45", base + 45.0),
                FilaTarifaMinicine("3h 45 min", "225 min", 225, "105 min", "Bs. $baseInt + Bs. 50", base + 50.0),
                FilaTarifaMinicine("4 horas exactas", "240 min", 240, "120 min (2 horas)", "Bs. $baseInt + Bs. 60", base + 60.0)
            )
        }

    fun calcular(minutos: Int): Double {
        val base = com.justplay.pos.core.models.BasePriceConfig.precioMinicine
        if (minutos <= 0) return base
        if (minutos <= 120) return base
        if (minutos <= 125) return base + 5.0
        if (minutos <= 135) return base + 10.0
        if (minutos <= 150) return base + 15.0
        if (minutos <= 165) return base + 20.0
        if (minutos <= 180) return base + 30.0
        if (minutos <= 195) return base + 35.0
        if (minutos <= 210) return base + 45.0
        if (minutos <= 225) return base + 50.0
        if (minutos <= 240) return base + 60.0

        val extraMin = minutos - 240
        val horasCompletas = extraMin / 60
        val remMin = extraMin % 60
        val costoRem = when {
            remMin == 0 -> 0.0
            remMin <= 15 -> 5.0
            remMin <= 30 -> 15.0
            remMin <= 45 -> 20.0
            else -> 30.0
        }
        return base + 60.0 + (horasCompletas * 30.0) + costoRem
    }

    fun parsearTextoAMinutos(texto: String): Int {
        val t = texto.trim().lowercase()
        if (t.isEmpty() || t == "libre" || t == "tiempo libre") return 0
        if (t.contains(":")) {
            val parts = t.split(":")
            val h = parts[0].trim().toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            return h * 60 + m
        }
        val cleanNumber = t.filter { it.isDigit() }.toIntOrNull() ?: 0
        if (t.contains("h") && !t.contains("min")) {
            return cleanNumber * 60
        }
        return cleanNumber
    }
}

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

    val TABLA_OFICIAL: List<FilaTarifaMinicine> = listOf(
        FilaTarifaMinicine("Hasta 2 horas", "0 a 120 min", 120, "Ninguno", "Bs. 40 (Base)", 40.0),
        FilaTarifaMinicine("2h 5 min", "125 min", 125, "5 min", "Bs. 40 + Bs. 5", 45.0),
        FilaTarifaMinicine("2h 15 min", "135 min", 135, "15 min", "Bs. 40 + Bs. 10 (2 bloques)", 50.0),
        FilaTarifaMinicine("2h 30 min", "150 min", 150, "30 min", "Bs. 40 + Bs. 15", 55.0),
        FilaTarifaMinicine("2h 45 min", "165 min", 165, "45 min", "Bs. 40 + Bs. 20", 60.0),
        FilaTarifaMinicine("3 horas exactas", "180 min", 180, "60 min (1 hora)", "Bs. 40 + Bs. 30", 70.0),
        FilaTarifaMinicine("3h 15 min", "195 min", 195, "75 min", "Bs. 40 + Bs. 35", 75.0),
        FilaTarifaMinicine("3h 30 min", "210 min", 210, "90 min", "Bs. 40 + Bs. 45", 85.0),
        FilaTarifaMinicine("3h 45 min", "225 min", 225, "105 min", "Bs. 40 + Bs. 50", 90.0),
        FilaTarifaMinicine("4 horas exactas", "240 min", 240, "120 min (2 horas)", "Bs. 40 + Bs. 60", 100.0)
    )

    fun calcular(minutos: Int): Double {
        if (minutos <= 0) return 40.0
        if (minutos <= 120) return 40.0
        if (minutos <= 125) return 45.0
        if (minutos <= 135) return 50.0
        if (minutos <= 150) return 55.0
        if (minutos <= 165) return 60.0
        if (minutos <= 180) return 70.0
        if (minutos <= 195) return 75.0
        if (minutos <= 210) return 85.0
        if (minutos <= 225) return 90.0
        if (minutos <= 240) return 100.0

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
        return 100.0 + (horasCompletas * 30.0) + costoRem
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

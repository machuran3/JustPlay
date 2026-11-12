package com.justplay.pos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.justplay.pos.core.utils.MinicineTarifaCalculator
import com.justplay.pos.ui.theme.*

@Composable
fun MinicineTarifaDialog(
    onDismiss: () -> Unit,
    onSelectTarifa: ((minutos: Int, precio: Double) -> Unit)? = null
) {
    var simulatorInput by remember { mutableStateOf("") }
    val simulatedMinutes = remember(simulatorInput) {
        MinicineTarifaCalculator.parsearTextoAMinutos(simulatorInput)
    }
    val simulatedPrice = remember(simulatedMinutes) {
        MinicineTarifaCalculator.calcular(simulatedMinutes)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BgDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .width(760.dp)
                .fillMaxHeight(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⏱️", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tabla de Cálculo de Hasta 4 Horas",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Minicine JustPlay • Tarifa base Bs. 40 (0 a 120 min) y desglose de horas extra",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tabla de Tarifas
                Surface(
                    color = CardDark,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceDark)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("DURACIÓN", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
                            Text("MINUTOS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f))
                            Text("TIEMPO EXTRA", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f))
                            Text("CÁLCULO", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.6f))
                            Text("TOTAL", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.0f))
                        }

                        Divider(color = BorderDark, thickness = 1.dp)

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(MinicineTarifaCalculator.TABLA_OFICIAL) { index, item ->
                                val isEven = index % 2 == 0
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isEven) Color.Transparent else Color(0xFF131826))
                                        .clickable {
                                            onSelectTarifa?.invoke(item.minutos, item.totalBs)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.duracionTexto,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1.3f)
                                    )
                                    Text(
                                        text = item.tiempoMinutosTexto,
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                    Text(
                                        text = item.tiempoExtraTexto,
                                        color = if (item.tiempoExtraTexto == "Ninguno") TextMuted else WarningYellow,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1.4f)
                                    )
                                    Text(
                                        text = item.calculoTexto,
                                        color = TextMuted,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1.6f)
                                    )
                                    Surface(
                                        color = AccentGreen.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "${item.totalBs.toInt()} Bs",
                                            color = AccentGreen,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Divider(color = BorderDark.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Simulador / Calculador rápido interactivo
                Surface(
                    color = CardDark,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentLime.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("💡 Simulador:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentLime)
                        OutlinedTextField(
                            value = simulatorInput,
                            onValueChange = { simulatorInput = it },
                            placeholder = { Text("Ej: 2:45 o 165 min", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.width(170.dp).height(46.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BgDark,
                                unfocusedContainerColor = BgDark,
                                focusedBorderColor = AccentLime,
                                unfocusedBorderColor = BorderDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        if (simulatedMinutes > 0) {
                            Text(
                                text = "Equivale a $simulatedMinutes min ➔ Tarifa: ",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${simulatedPrice.toInt()} Bs",
                                color = AccentGreen,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "Escribe una duración para calcular la tarifa exacta según la tabla",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (simulatedMinutes > 0 && onSelectTarifa != null) {
                            Button(
                                onClick = { onSelectTarifa(simulatedMinutes, simulatedPrice) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = Color.Black),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Usar Tarifa", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botón Cerrar
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Entendido / Cerrar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

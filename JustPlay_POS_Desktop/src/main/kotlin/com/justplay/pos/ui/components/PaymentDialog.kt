package com.justplay.pos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.justplay.pos.core.models.PaymentMethod
import com.justplay.pos.core.models.RoomState
import com.justplay.pos.ui.state.PosUiState
import com.justplay.pos.ui.theme.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun PaymentDialog(
    state: PosUiState,
    onPaymentMethodChange: (PaymentMethod) -> Unit,
    onCashReceivedChange: (String) -> Unit,
    onClientNameChange: (String) -> Unit,
    onClientPhoneChange: (String) -> Unit,
    onDestinationTypeChange: (String) -> Unit,
    onTargetRoomSelected: (roomId: Int?, roomName: String?) -> Unit,
    onRequestQrValidation: () -> Unit = {},
    onCancelQrValidation: () -> Unit = {},
    onConfirmCheckout: () -> Unit,
    onDismiss: () -> Unit
) {
    val occupiedRooms = remember(state.rooms) {
        state.rooms.filter { it.estado == RoomState.OCUPADA }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val scrollState = rememberScrollState()
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .width(520.dp)
                .heightIn(max = 720.dp)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                        if (!state.isProcessingSale) {
                            onConfirmCheckout()
                        }
                        true
                    } else {
                        false
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "💳 Finalizar y Cobrar Venta (Enter ↵)",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Gran Total a Cobrar
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("TOTAL DE LA CUENTA", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${String.format("%.2f", state.totalAmount)} Bs",
                            color = AccentGreen,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector de Destino: Mostrador vs Cargar a Sala
                Text("Destino de la Venta:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isMostrador = state.saleDestinationType == "MOSTRADOR"
                    Button(
                        onClick = { onDestinationTypeChange("MOSTRADOR") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMostrador) AccentLime else SurfaceDark,
                            contentColor = if (isMostrador) Color.Black else TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("🏬 Venta Mostrador", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    val isSala = state.saleDestinationType == "SALA"
                    Button(
                        onClick = {
                            onDestinationTypeChange("SALA")
                            if (occupiedRooms.isNotEmpty() && state.selectedTargetRoomId == null) {
                                onTargetRoomSelected(occupiedRooms.first().id, occupiedRooms.first().nombre)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSala) AccentLime else SurfaceDark,
                            contentColor = if (isSala) Color.Black else TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("🛋️ Cargar a Sala (${occupiedRooms.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Si se selecciona Sala, selector de salas ocupadas
                if (state.saleDestinationType == "SALA") {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (occupiedRooms.isEmpty()) {
                        Text(
                            text = "⚠️ No hay salas ocupadas actualmente. La venta se registrará como mostrador.",
                            color = Color(0xFFFF9800),
                            fontSize = 11.sp
                        )
                    } else {
                        Text("Selecciona la Sala Destino:", color = TextSecondary, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            occupiedRooms.take(4).forEach { room ->
                                val isSelected = state.selectedTargetRoomId == room.id
                                Surface(
                                    color = if (isSelected) AccentGreen else SurfaceDark,
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) AccentGreen else BorderDark),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onTargetRoomSelected(room.id, room.nombre) }
                                ) {
                                    Text(
                                        text = "${room.nombre}\n(${room.cliente ?: "Cliente"})",
                                        color = if (isSelected) Color.Black else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Método de Pago (Efectivo / QR / Mixto)
                Text("Método de Pago:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentMethod.values().forEach { method ->
                        val isSelected = state.selectedPaymentMethod == method
                        OutlinedButton(
                            onClick = { onPaymentMethodChange(method) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) AccentLime else SurfaceDark,
                                contentColor = if (isSelected) Color.Black else TextPrimary
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) AccentLime else BorderDark),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text(
                                text = when (method) {
                                    PaymentMethod.EFECTIVO -> "💵 Efectivo"
                                    PaymentMethod.QR -> "📱 QR"
                                    PaymentMethod.MIXTO -> "🔀 Mixto"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sección de Efectivo / Cálculo de Cambio
                if (state.selectedPaymentMethod == PaymentMethod.EFECTIVO || state.selectedPaymentMethod == PaymentMethod.MIXTO) {
                    Text(
                        text = if (state.selectedPaymentMethod == PaymentMethod.EFECTIVO) "Monto Recibido en Efectivo (Bs):" else "Monto en Efectivo:",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = state.cashReceivedAmount,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == '.' }
                            if (filtered.count { it == '.' } <= 1) {
                                onCashReceivedChange(filtered)
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedBorderColor = AccentLime,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Botones rápidos de billetes comunes
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(10, 20, 50, 100, 200).forEach { bill ->
                            Surface(
                                color = SurfaceDark,
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onCashReceivedChange(bill.toString()) }
                            ) {
                                Text(
                                    text = "$bill Bs",
                                    color = AccentLime,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Cálculo de Cambio o Saldo QR
                    val received = state.cashReceivedAmount.toDoubleOrNull() ?: state.totalAmount
                    if (state.selectedPaymentMethod == PaymentMethod.EFECTIVO) {
                        val change = maxOf(0.0, received - state.totalAmount)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceDark, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cambio / Vuelto:", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = "${String.format("%.2f", change)} Bs",
                                color = WarningYellow,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (state.selectedPaymentMethod == PaymentMethod.MIXTO) {
                        val efec = state.cashReceivedAmount.toDoubleOrNull() ?: 0.0
                        val qrSaldo = maxOf(0.0, state.totalAmount - efec)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceDark, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📱 Saldo Restante por QR:", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = "${String.format("%.2f", qrSaldo)} Bs",
                                color = AccentLime,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Sección QR: Validación Push / Socket en tiempo real con Móviles
                if (state.selectedPaymentMethod == PaymentMethod.QR || state.selectedPaymentMethod == PaymentMethod.MIXTO) {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (state.isWaitingQrValidation) {
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentLime),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AccentLime, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = state.qrValidationStatusMessage ?: "Esperando comprobante desde teléfono móvil...",
                                    color = AccentLime,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onCancelQrValidation,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350), contentColor = Color.White),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Cancelar Espera Móvil", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = onRequestQrValidation,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5), contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Text("📱 Solicitar Validación y Comprobante al Celular", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Botón Confirmar Cobro (Enter)
                Button(
                    onClick = onConfirmCheckout,
                    enabled = !state.isProcessingSale,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = Color.Black,
                        disabledContainerColor = BorderDark,
                        disabledContentColor = TextSecondary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (state.isProcessingSale) {
                        CircularProgressIndicator(color = AccentLime, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Procesando venta...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Completar Venta y Sincronizar (Enter ↵)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

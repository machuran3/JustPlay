package com.justplay.pos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justplay.pos.core.models.SyncStatus
import com.justplay.pos.ui.state.PosUiState
import com.justplay.pos.ui.theme.*

@Composable
fun TopStatusBar(
    state: PosUiState,
    onSyncClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onNewProductClick: () -> Unit,
    onExportMasterClick: () -> Unit = {},
    onDismissExportNotification: () -> Unit = {},
    onCheckUpdatesClick: () -> Unit = {}
) {
    Surface(
        color = CardDark,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
            // Logo & Título
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🎮",
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "JustPlay",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "POS Scanner",
                            color = AccentLime,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                            modifier = Modifier.clickable { onCheckUpdatesClick() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v${state.currentAppVersion}",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "🔄",
                                    fontSize = 9.sp
                                )
                            }
                        }

                        if (state.availableUpdate != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = onCheckUpdatesClick,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text(
                                    text = "🚀 Actualizar (${state.availableUpdate.tagName})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (state.isCheckingForUpdate) {
                            Spacer(modifier = Modifier.width(6.dp))
                            CircularProgressIndicator(color = AccentLime, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        }
                    }
                    Text(
                        text = "Punto de Venta & Lector de Códigos de Barras • ID: ${if (state.deviceId.length > 8) state.deviceId.take(8) + "..." else state.deviceId}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Indicador de Sincronización con Firebase
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val (statusColor, statusText) = when (state.syncStatus) {
                    SyncStatus.ONLINE_SYNCED -> Pair(AccentGreen, "🟢 Firebase En Línea")
                    SyncStatus.OFFLINE_SAVED -> Pair(WarningYellow, "🟡 Modo Offline (Guardado local)")
                    SyncStatus.SYNCING -> Pair(AccentLime, "⏳ Sincronizando...")
                    SyncStatus.ERROR -> Pair(AlertRed, "🔴 Error de conexión")
                }

                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Botón Sincronizar
                IconButton(
                    onClick = onSyncClick,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = AccentLime)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sincronizar con Firebase"
                    )
                }

                // Botón Historial de Ventas
                OutlinedButton(
                    onClick = onHistoryClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AccentLime
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ventas (${state.recentSales.size})", fontSize = 12.sp)
                }

                // Botón Exportación General (Master Export)
                OutlinedButton(
                    onClick = onExportMasterClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    enabled = !state.isExportingMaster
                ) {
                    if (state.isExportingMaster) {
                        CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Text("📊", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text("Exportación General", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Botón Agregar Producto al Catálogo
                Button(
                    onClick = onNewProductClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentLime,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nuevo Producto", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Banner informativo tras exportación general
        if (state.masterExportNotification != null) {
            Surface(
                color = if (state.masterExportNotification!!.startsWith("❌")) AlertRed.copy(alpha = 0.2f) else AccentGreen.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (state.masterExportNotification!!.startsWith("❌")) AlertRed else AccentGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.masterExportNotification!!,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onDismissExportNotification,
                        colors = ButtonDefaults.buttonColors(containerColor = CardDark, contentColor = Color.White),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Entendido", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
}

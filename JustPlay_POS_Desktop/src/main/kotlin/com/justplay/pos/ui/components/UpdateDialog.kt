package com.justplay.pos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.justplay.pos.services.ReleaseInfo
import com.justplay.pos.ui.theme.*

@Composable
fun UpdateDialog(
    release: ReleaseInfo,
    currentVersion: String,
    isDownloading: Boolean,
    downloadProgress: Float,
    isDownloaded: Boolean,
    statusMessage: String?,
    onStartDownload: () -> Unit,
    onApplyUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            modifier = Modifier.width(520.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🚀 Actualización Disponible",
                            color = AccentLime,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Versión actual: v$currentVersion ➔ Nueva: ${release.tagName}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Surface(
                        color = AccentGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = release.tagName,
                            color = AccentGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Título y Notas de Versión
                Text(
                    text = release.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Novedades y Cambios:",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = release.changelog,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Estado de descarga / Progreso
                if (isDownloading) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Descargando actualización...",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                color = AccentLime,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = AccentLime,
                            trackColor = SurfaceDark
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (isDownloaded) {
                    Surface(
                        color = AccentGreen.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✅", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Paquete listo para aplicar. Pulsa el botón abajo para instalar y reiniciar.",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (statusMessage != null && !isDownloading) {
                    Text(
                        text = statusMessage,
                        color = if (statusMessage.startsWith("Error")) AlertRed else TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Botones de Acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isDownloading && !isDownloaded) {
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Text("Más Tarde", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    if (isDownloaded) {
                        Button(
                            onClick = onApplyUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Text("🔄 Instalar y Abrir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else if (isDownloading) {
                        Button(
                            onClick = {},
                            enabled = false,
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            CircularProgressIndicator(color = AccentLime, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Descargando...", fontSize = 12.sp, color = TextMuted)
                        }
                    } else {
                        Button(
                            onClick = onStartDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentLime, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Text("⬇️ Descargar Actualización", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

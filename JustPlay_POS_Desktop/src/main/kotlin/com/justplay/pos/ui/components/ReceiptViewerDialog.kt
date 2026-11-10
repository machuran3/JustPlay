package com.justplay.pos.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.justplay.pos.core.utils.ReceiptImageUtils
import com.justplay.pos.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReceiptViewerDialog(
    receiptString: String,
    title: String = "Comprobantes de Pago",
    initialIndex: Int = 0,
    onDeletePhoto: ((newReceiptString: String?) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var rawString by remember(receiptString) { mutableStateOf(receiptString) }
    val photoUrls = remember(rawString) { ReceiptImageUtils.splitReceiptUrls(rawString) }

    if (photoUrls.isEmpty()) {
        onDismiss()
        return
    }

    var currentIndex by remember { mutableStateOf(initialIndex.coerceIn(0, photoUrls.size - 1)) }
    if (currentIndex >= photoUrls.size) {
        currentIndex = (photoUrls.size - 1).coerceAtLeast(0)
    }

    val currentUrl = photoUrls.getOrNull(currentIndex) ?: ""

    var currentBitmap by remember(currentUrl) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(currentUrl) { mutableStateOf(true) }
    var hasError by remember(currentUrl) { mutableStateOf(false) }

    LaunchedEffect(currentUrl) {
        isLoading = true
        hasError = false
        val bmp = withContext(Dispatchers.IO) {
            ReceiptImageUtils.loadComposeBitmap(currentUrl)
        }
        currentBitmap = bmp
        isLoading = false
        hasError = (bmp == null)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF141722),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .width(760.dp)
                .heightIn(min = 540.dp, max = 680.dp)
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "📸 $title",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentLime.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentLime.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Foto ${currentIndex + 1} de ${photoUrls.size}",
                                color = AccentLime,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Área Principal de Visualización
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0D0F17), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF262D42), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AccentLime, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Cargando comprobante...", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                        hasError || currentBitmap == null -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Text("❌ No se pudo decodificar la imagen", color = AlertRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("El formato o enlace del comprobante no es accesible.", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                        else -> {
                            Image(
                                bitmap = currentBitmap!!,
                                contentDescription = "Comprobante ${currentIndex + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }

                    // Botones flotantes de navegación rápida (Izquierda / Derecha)
                    if (photoUrls.size > 1) {
                        if (currentIndex > 0) {
                            IconButton(
                                onClick = { currentIndex-- },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 12.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    .size(40.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Anterior", tint = Color.White)
                            }
                        }

                        if (currentIndex < photoUrls.size - 1) {
                            IconButton(
                                onClick = { currentIndex++ },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 12.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    .size(40.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Siguiente", tint = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fila Inferior de Controles y Miniaturas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Controles de navegación de fotos
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (photoUrls.size > 1) {
                            Button(
                                onClick = { if (currentIndex > 0) currentIndex-- },
                                enabled = currentIndex > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = CardDark, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("◀ Anterior", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Indicadores circulares
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                photoUrls.forEachIndexed { idx, _ ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (idx == currentIndex) 10.dp else 7.dp)
                                            .background(
                                                if (idx == currentIndex) AccentLime else Color(0xFF4A5568),
                                                CircleShape
                                            )
                                            .clickable { currentIndex = idx }
                                    )
                                }
                            }

                            Button(
                                onClick = { if (currentIndex < photoUrls.size - 1) currentIndex++ },
                                enabled = currentIndex < photoUrls.size - 1,
                                colors = ButtonDefaults.buttonColors(containerColor = CardDark, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Siguiente ▶", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Acciones de pie: Eliminar foto (opcional) y Cerrar
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onDeletePhoto != null) {
                            Button(
                                onClick = {
                                    val newString = ReceiptImageUtils.removeReceiptUrl(rawString, currentIndex)
                                    rawString = newString ?: ""
                                    onDeletePhoto(newString)
                                    if (newString == null) {
                                        onDismiss()
                                    } else {
                                        val newUrls = ReceiptImageUtils.splitReceiptUrls(newString)
                                        if (currentIndex >= newUrls.size) {
                                            currentIndex = (newUrls.size - 1).coerceAtLeast(0)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.85f), contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Eliminar Foto", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238), contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Cerrar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

package com.justplay.pos.core.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.max

/**
 * Utilidades para manejo seguro de comprobantes únicos y múltiples en la aplicación de escritorio.
 * Estandarizado con el delimitador "|||" usado en Android (AdminJustPlay / Employ_JustPlay).
 */
object ReceiptImageUtils {

    const val SEPARADOR = "|||"
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(java.time.Duration.ofSeconds(10))
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .readTimeout(java.time.Duration.ofSeconds(10))
            .build()
    }

    /**
     * Separa una cadena de comprobantes de forma segura (soporta delimitador moderno "|||",
     * listas antiguas separadas por coma, y cadenas individuales).
     */
    fun splitReceiptUrls(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val str = raw.trim()

        if (str.contains(SEPARADOR)) {
            return str.split(SEPARADOR).map { it.trim() }.filter { it.isNotEmpty() }
        }

        if (str.contains(",") && !str.startsWith("data:image") && !str.startsWith("http") && str.length < 500) {
            return str.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

        return listOf(str)
    }

    /**
     * Une una lista de URLs / cadenas Base64 usando el delimitador seguro "|||".
     */
    fun joinReceiptUrls(urls: List<String>): String {
        return urls.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(SEPARADOR)
    }

    /**
     * Agrega nuevas fotos a una cadena existente de comprobantes.
     */
    fun addReceiptUrls(existing: String?, newUrls: List<String>): String {
        val currentList = splitReceiptUrls(existing).toMutableList()
        for (item in newUrls) {
            val clean = item.trim()
            if (clean.isNotEmpty() && !currentList.contains(clean)) {
                currentList.add(clean)
            }
        }
        return joinReceiptUrls(currentList)
    }

    /**
     * Elimina el comprobante en la posición indicada.
     */
    fun removeReceiptUrl(existing: String?, index: Int): String? {
        val currentList = splitReceiptUrls(existing).toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
        }
        return if (currentList.isEmpty()) null else joinReceiptUrls(currentList)
    }

    /**
     * Abre el selector nativo de archivos del sistema operativo permitiendo selección múltiple de fotos.
     */
    fun pickImagesFromNativeDialog(title: String = "Seleccionar Comprobante(s) de Pago"): List<File> {
        return try {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            dialog.isMultipleMode = true
            dialog.setFilenameFilter { _, name ->
                val lower = name.lowercase()
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                        lower.endsWith(".webp") || lower.endsWith(".bmp")
            }
            dialog.isVisible = true
            dialog.files?.toList() ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Lee un archivo de imagen, lo optimiza/comprime si excede la dimensión máxima y lo devuelve
     * codificado como Base64 data URL compatible con Firebase y el sistema móvil.
     */
    fun processAndCompressImageFile(file: File, maxDimension: Int = 1200): String {
        return try {
            val original: BufferedImage? = ImageIO.read(file)
            if (original == null) {
                return fallbackFileToBase64(file)
            }

            val w = original.width
            val h = original.height
            val maxDim = max(w, h)

            val scaled: BufferedImage = if (maxDim > maxDimension) {
                val scale = maxDimension.toDouble() / maxDim
                val targetW = (w * scale).toInt()
                val targetH = (h * scale).toInt()
                val targetImg = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB)
                val g2d = targetImg.createGraphics()
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g2d.drawImage(original, 0, 0, targetW, targetH, null)
                g2d.dispose()
                targetImg
            } else {
                val targetImg = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
                val g2d = targetImg.createGraphics()
                g2d.drawImage(original, 0, 0, null)
                g2d.dispose()
                targetImg
            }

            val baos = ByteArrayOutputStream()
            ImageIO.write(scaled, "jpg", baos)
            val base64 = Base64.getEncoder().encodeToString(baos.toByteArray())
            "data:image/jpeg;base64,$base64"
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackFileToBase64(file)
        }
    }

    private fun fallbackFileToBase64(file: File): String {
        val bytes = file.readBytes()
        val base64 = Base64.getEncoder().encodeToString(bytes)
        val mime = when {
            file.name.endsWith(".png", ignoreCase = true) -> "image/png"
            file.name.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> "image/jpeg"
        }
        return "data:$mime;base64,$base64"
    }

    /**
     * Decodifica una fuente de comprobante (URL HTTPS de Firebase Storage, Base64 data URI o archivo local)
     * a un [ImageBitmap] de Compose Desktop para previsualización inmediata.
     */
    fun loadComposeBitmap(source: String): ImageBitmap? {
        val clean = source.trim()
        if (clean.isEmpty()) return null

        return try {
            val bytes: ByteArray? = when {
                clean.startsWith("http://") || clean.startsWith("https://") -> {
                    val request = Request.Builder().url(clean).build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) response.body?.bytes() else null
                }
                clean.startsWith("data:image") || (clean.length > 100 && !clean.startsWith("/") && !clean.startsWith("file:")) -> {
                    val rawBase64 = if (clean.contains(",")) clean.substringAfter(",") else clean
                    val sanitized = rawBase64.replace("\n", "").replace("\r", "").replace(" ", "").trim()
                    Base64.getDecoder().decode(sanitized)
                }
                File(clean).exists() -> {
                    File(clean).readBytes()
                }
                else -> null
            }

            if (bytes != null && bytes.isNotEmpty()) {
                Image.makeFromEncoded(bytes).toComposeImageBitmap()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

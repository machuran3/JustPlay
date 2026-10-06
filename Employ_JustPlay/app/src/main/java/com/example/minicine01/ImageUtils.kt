package com.example.minicine01

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.max

object ImageUtils {

    const val SEPARADOR = "|||"
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Une dos comprobantes usando el delimitador seguro "|||" que no entra en conflicto con Base64 ni URLs.
     */
    fun agregarComprobante(actual: String?, nuevo: String?): String? {
        if (nuevo.isNullOrBlank()) return actual
        if (actual.isNullOrBlank()) return nuevo
        return "$actual$SEPARADOR$nuevo"
    }

    /**
     * Separa una cadena de comprobantes de forma segura.
     */
    fun separarRutasComprobantes(rawString: String?): List<String> {
        if (rawString.isNullOrBlank()) return emptyList()
        val str = rawString.trim()

        // 1. Delimitador moderno "|||"
        if (str.contains(SEPARADOR)) {
            return str.split(SEPARADOR).map { it.trim() }.filter { it.isNotEmpty() }
        }

        // 2. Si es una lista antigua de rutas locales separadas por coma
        if (str.contains(",") && !str.startsWith("data:image") && !str.startsWith("http") && str.length < 500) {
            return str.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

        // 3. Comprobante único
        return listOf(str)
    }

    /**
     * Crea un archivo temporal y devuelve su URI seguro mediante FileProvider para la cámara.
     */
    fun createTempCameraUri(context: Context): Uri {
        val storageDir = File(context.cacheDir, "camera_temp")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        val tempFile = File.createTempFile("CAMERA_", ".jpg", storageDir)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tempFile
        )
    }

    /**
     * Procesa la imagen, la optimiza y la sube a Firebase Storage o genera un Base64 de respaldo.
     * Esto garantiza que la foto sea visible inmediatamente en cualquier otro celular.
     */
    fun procesarYSubirComprobante(context: Context, uri: Uri, onComplete: (String?) -> Unit) {
        Thread {
            try {
                val bitmap = decodificarBitmapOrientado(context, uri, maxDimension = 850)
                if (bitmap == null) {
                    mainHandler.post { onComplete(null) }
                    return@Thread
                }

                // 1. Guardar copia local en caché
                try {
                    val dirComprobantes = File(context.filesDir, "comprobantes")
                    if (!dirComprobantes.exists()) dirComprobantes.mkdirs()
                    val destinoFile = File(dirComprobantes, "qr_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(destinoFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                    }
                } catch (_: Exception) {}

                // 2. Comprimir a JPEG
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                val byteArray = outputStream.toByteArray()

                // 3. Generar Base64 como respaldo garantizado
                val base64Str = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                val payloadBase64 = "data:image/jpeg;base64,$base64Str"

                // 4. Intentar subir a Firebase Storage para obtener URL limpia
                try {
                    val storageRef = FirebaseStorage.getInstance().reference
                    val photoRef = storageRef.child("comprobantes/qr_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")

                    photoRef.putBytes(byteArray)
                        .continueWithTask { task ->
                            if (!task.isSuccessful) {
                                task.exception?.let { throw it }
                            }
                            photoRef.downloadUrl
                        }
                        .addOnSuccessListener { downloadUri ->
                            mainHandler.post { onComplete(downloadUri.toString()) }
                        }
                        .addOnFailureListener {
                            // Si Storage falla por reglas o conexión, usa el Base64 garantizado
                            mainHandler.post { onComplete(payloadBase64) }
                        }
                } catch (e: Exception) {
                    mainHandler.post { onComplete(payloadBase64) }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post { onComplete(null) }
            }
        }.start()
    }

    /**
     * Versión síncrona para compatibilidad que comprime a Base64.
     */
    fun comprimirYGuardarImagen(context: Context, uri: Uri): String? {
        return try {
            val bitmap = decodificarBitmapOrientado(context, uri, maxDimension = 850) ?: return null
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64Str = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64Str"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Obtiene un Bitmap en segundo plano (soporta URLs de Firebase Storage, Base64 de la nube, archivos locales y URIs).
     */
    fun cargarBitmapAsync(context: Context, pathOrUrlOrBase64: String, onResult: (Bitmap?) -> Unit) {
        Thread {
            val bitmap = obtenerBitmap(context, pathOrUrlOrBase64)
            mainHandler.post { onResult(bitmap) }
        }.start()
    }

    /**
     * Obtiene un Bitmap síncrono.
     */
    fun obtenerBitmap(context: Context, pathOrBase64OrUri: String): Bitmap? {
        val entrada = pathOrBase64OrUri.trim()
        if (entrada.isEmpty()) return null

        return try {
            // Caso 1: URL de Firebase Storage (https://...)
            if (entrada.startsWith("http://") || entrada.startsWith("https://")) {
                val url = URL(entrada)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.doInput = true
                connection.connect()
                val input = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(input)
                input.close()
                bitmap
            }
            // Caso 2: Cadena Base64 (sincronizada desde la nube / otro celular)
            else if (entrada.startsWith("data:image") || (entrada.length > 100 && !entrada.startsWith("/") && !entrada.startsWith("content:"))) {
                val base64Data = if (entrada.contains(",")) entrada.substringAfter(",") else entrada
                val cleanBase64 = base64Data.replace("\n", "").replace("\r", "").replace(" ", "").trim()
                val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)
            }
            // Caso 3: Archivo local en este dispositivo
            else if (File(entrada).exists()) {
                BitmapFactory.decodeFile(File(entrada).absolutePath)
            }
            // Caso 4: URI de contenido
            else {
                val uri = Uri.parse(entrada)
                decodificarBitmapOrientado(context, uri, 850)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decodifica una imagen optimizando memoria y respetando la orientación EXIF de la cámara.
     */
    private fun decodificarBitmapOrientado(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null

            var inSampleSize = 1
            val maxSrc = max(srcWidth, srcHeight)
            while (maxSrc / (inSampleSize * 2) >= maxDimension) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            inputStream = context.contentResolver.openInputStream(uri)
            val decodedBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions) ?: return null
            inputStream?.close()

            val rotacion = obtenerRotacionExif(context, uri)
            if (rotacion != 0) {
                val matrix = Matrix().apply { postRotate(rotacion.toFloat()) }
                Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true)
            } else {
                decodedBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    private fun obtenerRotacionExif(context: Context, uri: Uri): Int {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return 0
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            inputStream.close()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Une una lista de comprobantes usando el delimitador seguro "|||".
     */
    fun juntarRutasComprobantes(rutas: List<String>): String {
        return rutas.filter { it.isNotBlank() }.joinToString(SEPARADOR)
    }

    /**
     * Muestra un diálogo emergente con el comprobante ampliado, cargando la foto de forma asíncrona
     * y permitiendo eliminar fotos una por una si se proporciona el callback onEliminarFoto.
     */
    fun mostrarDialogoVerComprobante(
        context: Context,
        imagePathOrUri: String,
        titulo: String = "Comprobante de Pago QR",
        onEliminarFoto: ((nuevaCadena: String?) -> Unit)? = null
    ) {
        try {
            val rutas = separarRutasComprobantes(imagePathOrUri).toMutableList()
            if (rutas.isEmpty()) return

            var indiceActual = 0

            val view = LayoutInflater.from(context).inflate(R.layout.dialog_ver_comprobante, null)
            val dialog = AlertDialog.Builder(context).setView(view).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val tvTitulo = view.findViewById<TextView>(R.id.tvTituloComprobante)
            val ivFoto = view.findViewById<ImageView>(R.id.ivFotoComprobante)
            val btnCerrar = view.findViewById<TextView>(R.id.btnCerrarComprobante)
            val btnAceptar = view.findViewById<Button>(R.id.btnAceptarComprobante)
            val btnEliminarFoto = view.findViewById<Button>(R.id.btnEliminarFotoComprobante)

            val llControlesNavegacion = view.findViewById<LinearLayout>(R.id.llControlesNavegacion)
            val btnAnteriorFoto = view.findViewById<ImageButton>(R.id.btnAnteriorFoto)
            val btnSiguienteFoto = view.findViewById<ImageButton>(R.id.btnSiguienteFoto)
            val tvContadorFotos = view.findViewById<TextView>(R.id.tvContadorFotos)
            val pbCargando = view.findViewById<ProgressBar>(R.id.pbCargandoComprobante)

            tvTitulo.text = titulo

            if (onEliminarFoto != null) {
                btnEliminarFoto?.visibility = View.VISIBLE
            }

            fun actualizarControlesVisibles() {
                if (rutas.size > 1) {
                    llControlesNavegacion?.visibility = View.VISIBLE
                    tvContadorFotos?.visibility = View.VISIBLE
                } else {
                    llControlesNavegacion?.visibility = View.GONE
                    tvContadorFotos?.visibility = View.GONE
                }
            }
            actualizarControlesVisibles()

            fun cargarFoto(indice: Int) {
                try {
                    if (indice !in rutas.indices) return
                    val rutaActual = rutas[indice]
                    ivFoto.setImageBitmap(null)
                    pbCargando?.visibility = View.VISIBLE

                    cargarBitmapAsync(context, rutaActual) { bitmap ->
                        pbCargando?.visibility = View.GONE
                        if (bitmap != null) {
                            ivFoto.setImageBitmap(bitmap)
                        } else {
                            Toast.makeText(context, "No se pudo cargar la imagen del comprobante", Toast.LENGTH_SHORT).show()
                        }
                    }

                    tvContadorFotos?.text = "Foto ${indice + 1} de ${rutas.size}"
                    btnAnteriorFoto?.visibility = if (indice > 0) View.VISIBLE else View.INVISIBLE
                    btnSiguienteFoto?.visibility = if (indice < rutas.size - 1) View.VISIBLE else View.INVISIBLE
                } catch (e: Exception) {
                    e.printStackTrace()
                    pbCargando?.visibility = View.GONE
                }
            }

            cargarFoto(indiceActual)

            btnEliminarFoto?.setOnClickListener {
                if (rutas.isEmpty()) return@setOnClickListener
                val textoFoto = if (rutas.size > 1) "la foto ${indiceActual + 1} de ${rutas.size}" else "el comprobante"
                AlertDialog.Builder(context)
                    .setTitle("🗑️ Eliminar comprobante")
                    .setMessage("¿Deseas eliminar $textoFoto?")
                    .setPositiveButton("Sí, eliminar") { _, _ ->
                        rutas.removeAt(indiceActual)
                        if (rutas.isEmpty()) {
                            onEliminarFoto?.invoke(null)
                            dialog.dismiss()
                            Toast.makeText(context, "Comprobante eliminado", Toast.LENGTH_SHORT).show()
                        } else {
                            val nuevaCadena = juntarRutasComprobantes(rutas)
                            onEliminarFoto?.invoke(nuevaCadena)
                            if (indiceActual >= rutas.size) {
                                indiceActual = rutas.size - 1
                            }
                            actualizarControlesVisibles()
                            cargarFoto(indiceActual)
                            Toast.makeText(context, "Foto eliminada", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }

            btnAnteriorFoto?.setOnClickListener {
                if (indiceActual > 0) {
                    indiceActual--
                    cargarFoto(indiceActual)
                }
            }

            btnSiguienteFoto?.setOnClickListener {
                if (indiceActual < rutas.size - 1) {
                    indiceActual++
                    cargarFoto(indiceActual)
                }
            }

            btnCerrar?.setOnClickListener { dialog.dismiss() }
            btnAceptar?.setOnClickListener { dialog.dismiss() }

            dialog.show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error al abrir comprobante: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}


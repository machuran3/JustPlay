package com.example.adminjustplay

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modelo de datos para Terminales POS de Escritorio vinculadas.
 */
data class TerminalPos(
    val deviceId: String = "",
    val hardwareUuid: String = "",
    val nombre: String = "PC Terminal",
    val hostname: String = "",
    val osName: String = "",
    val sessionToken: String = "",
    val autorizado: Boolean = true,
    val ventasHabilitadas: Boolean = true,
    val estadoConexion: String = "OFFLINE", // "ONLINE", "OFFLINE", "DESVINCULADO"
    val lastHeartbeat: Long = 0L,
    val lastHeartbeatStr: String = "",
    val appVersion: String = "1.0.0",
    val ipAddress: String = "",
    val fechaVinculacion: String = ""
) {
    fun isCurrentlyOnline(): Boolean {
        if (!autorizado || estadoConexion == "DESVINCULADO") return false
        val diff = System.currentTimeMillis() - lastHeartbeat
        return diff in 0..60000
    }
}

/**
 * Single Responsibility Principle (SRP):
 * Administra exclusivamente la autorización y vinculación de terminales POS de escritorio,
 * el control de versiones mínimas (Force Update) y la gestión del catálogo de productos e inventario.
 */
class PosManagementManager(
    private val context: Context,
    private val database: DatabaseReference
) {
    private val productAuditManager = ProductAuditManager(context, database)

    fun mostrarDialogoProductos() {
        val scrollView = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
            setBackgroundColor(Color.parseColor("#1A1C29"))
        }
        scrollView.addView(container)

        val dialog = AlertDialog.Builder(context)
            .setView(scrollView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }
        val tvTitulo = TextView(context).apply {
            text = "📦 Catálogo de Productos"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnCerrar = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#A0A0A0"))
            textSize = 20f
            setPadding(10, 0, 10, 0)
            setOnClickListener { dialog.dismiss() }
        }
        header.addView(tvTitulo)
        header.addView(btnCerrar)
        container.addView(header)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 10)
        }

        val btnNuevoProducto = Button(context).apply {
            text = "➕ Nuevo Producto"
            setBackgroundColor(Color.parseColor("#DEFF9A"))
            setTextColor(Color.BLACK)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            setOnClickListener {
                mostrarDialogoCrearEditarProducto(null)
            }
        }

        val btnAuditoriaProd = Button(context).apply {
            text = "📜 Ver Auditoría"
            setBackgroundColor(Color.parseColor("#24283B"))
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                productAuditManager.mostrarDialogoHistorialAuditoriaProductos()
            }
        }

        btnRow.addView(btnNuevoProducto)
        btnRow.addView(btnAuditoriaProd)
        container.addView(btnRow)

        val llListaProductos = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 14, 0, 0)
        }
        container.addView(llListaProductos)

        val productosListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                llListaProductos.removeAllViews()
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    val tvVacio = TextView(context).apply {
                        text = "No hay productos registrados en el catálogo"
                        setTextColor(Color.parseColor("#888888"))
                        textSize = 12f
                        setPadding(0, 10, 0, 10)
                    }
                    llListaProductos.addView(tvVacio)
                    return
                }

                for (snap in snapshot.children) {
                    val barcode = snap.child("codigo_barras").getValue(String::class.java) ?: snap.key ?: ""
                    val nombre = snap.child("nombre").getValue(String::class.java) ?: "Producto $barcode"
                    val gramaje = snap.child("gramaje").getValue(String::class.java) ?: ""
                    val precio = snap.child("precio_unitario").getValue(Double::class.java) ?: 0.0
                    val stock = snap.child("stock_actual").getValue(Int::class.java) ?: 0
                    val categoria = snap.child("categoria").getValue(String::class.java) ?: "General"

                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(14, 12, 14, 12)
                        setBackgroundColor(Color.parseColor("#24283B"))
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.setMargins(0, 0, 0, 8)
                        layoutParams = lp
                        setOnClickListener {
                            mostrarDialogoCrearEditarProducto(barcode, nombre, precio, stock, categoria, gramaje)
                        }
                    }

                    val info = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val tvNom = TextView(context).apply {
                        val nomText = if (gramaje.isNotBlank()) "📦 $nombre ($gramaje)" else "📦 $nombre"
                        text = nomText
                        setTextColor(Color.WHITE)
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                    }

                    val tvSub = TextView(context).apply {
                        text = "Cód: $barcode | $categoria | Precio: $precio Bs"
                        setTextColor(Color.parseColor("#A0A0A0"))
                        textSize = 11f
                    }

                    val tvStock = TextView(context).apply {
                        text = "Stock: $stock"
                        setTextColor(if (stock <= 5) Color.parseColor("#FF5252") else Color.parseColor("#00E676"))
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(10, 0, 0, 0)
                    }

                    info.addView(tvNom)
                    info.addView(tvSub)
                    row.addView(info)
                    row.addView(tvStock)
                    llListaProductos.addView(row)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        database.child("productos_catalogo").addValueEventListener(productosListener)
        dialog.setOnDismissListener {
            database.child("productos_catalogo").removeEventListener(productosListener)
        }

        dialog.show()
    }

    fun mostrarDialogoGestionPOS() = mostrarDialogoProductos()

    fun mostrarDialogoCambiarVersionMinima() {
        val etVersion = EditText(context).apply {
            hint = "Ej: 1.0.5"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        database.child("config_pos").get().addOnSuccessListener { snap ->
            val v = snap.child("min_app_version").getValue(String::class.java)
                ?: snap.child("min_app_version_desktop").getValue(String::class.java)
                ?: snap.child("min_app_version_mobile").getValue(String::class.java)
                ?: "1.0.0"
            etVersion.setText(v)
        }

        AlertDialog.Builder(context)
            .setTitle("🛡️ Versión Mínima Requerida (Desktop y Celular)")
            .setMessage("Las aplicaciones de Celular (Admin y Empleado) y terminales Desktop con versiones inferiores a esta serán bloqueadas obligatoriamente hasta que se actualicen.")
            .setView(etVersion)
            .setPositiveButton("Guardar") { _, _ ->
                val newVer = etVersion.text.toString().trim()
                if (newVer.isNotEmpty()) {
                    val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                    val updates = mapOf<String, Any>(
                        "min_app_version" to newVer,
                        "min_app_version_desktop" to newVer,
                        "min_app_version_mobile" to newVer,
                        "ultima_modificacion_version" to fecha,
                        "modificado_por" to "Admin (${DeviceInfoProvider.getDeviceModel()})"
                    )
                    database.child("config_pos").updateChildren(updates)
                    // Sincronizar también con config_actualizaciones
                    database.child("config_actualizaciones").child("AdminJustPlay").child("minVersion").setValue(newVer)
                    database.child("config_actualizaciones").child("AdminJustPlay").child("esObligatoria").setValue(true)
                    database.child("config_actualizaciones").child("Employ_JustPlay").child("minVersion").setValue(newVer)
                    database.child("config_actualizaciones").child("Employ_JustPlay").child("esObligatoria").setValue(true)
                    Toast.makeText(context, "Versión mínima v$newVer guardada para Desktop y Celular", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun desvincularTerminal(terminal: TerminalPos) {
        AlertDialog.Builder(context)
            .setTitle("🔌 Desvincular ${terminal.nombre}")
            .setMessage("¿Estás seguro de desvincular esta PC? Su sesión se cerrará de inmediato en tiempo real y quedará bloqueada.")
            .setPositiveButton("Desvincular Ahora") { _, _ ->
                val updates = mapOf<String, Any>(
                    "autorizado" to false,
                    "estadoConexion" to "DESVINCULADO",
                    "sessionToken" to ""
                )
                database.child("terminales_pos").child(terminal.deviceId).updateChildren(updates)
                Toast.makeText(context, "Terminal desvinculada exitosamente.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun autorizarTerminal(terminal: TerminalPos) {
        val newToken = "TOK_${java.util.UUID.randomUUID().toString().take(12)}"
        val updates = mapOf<String, Any>(
            "autorizado" to true,
            "estadoConexion" to "OFFLINE",
            "sessionToken" to newToken,
            "fechaVinculacion" to SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        )
        database.child("terminales_pos").child(terminal.deviceId).updateChildren(updates)
        Toast.makeText(context, "Terminal autorizada con nuevo token de sesión.", Toast.LENGTH_SHORT).show()
    }

    private fun eliminarTerminal(terminal: TerminalPos) {
        AlertDialog.Builder(context)
            .setTitle("🗑️ Eliminar ${terminal.nombre}")
            .setMessage("¿Deseas borrar completamente el registro de esta terminal?")
            .setPositiveButton("Eliminar") { _, _ ->
                database.child("terminales_pos").child(terminal.deviceId).removeValue()
                Toast.makeText(context, "Terminal eliminada del registro.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun mostrarDialogoPreciosBase() {
        val scrollView = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.parseColor("#1A1C29"))
        }
        scrollView.addView(container)

        val tvIntro = TextView(context).apply {
            text = "Configura la tarifa base sugerida (por 1 hora) para cada sección. Se sincroniza con terminales de escritorio y aplicaciones de empleados."
            setTextColor(Color.parseColor("#A0A0A0"))
            textSize = 12f
            setPadding(0, 0, 0, 16)
        }
        container.addView(tvIntro)

        // Minicine
        val tvMinicine = TextView(context).apply {
            text = "🎬 Salas de Minicine (Bs / hora):"
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
        }
        val etMinicine = EditText(context).apply {
            hint = "Tarifa Minicine (ej. 35.0)"
            setText("35.0")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }
        container.addView(tvMinicine)
        container.addView(etMinicine)

        // Volantes
        val tvVolantes = TextView(context).apply {
            text = "🏎️ Simuladores y Volantes (Bs / hora):"
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 12, 0, 0)
        }
        val etVolantes = EditText(context).apply {
            hint = "Tarifa Volantes (ej. 15.0)"
            setText("15.0")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }
        container.addView(tvVolantes)
        container.addView(etVolantes)

        // Just Dance
        val tvJustDance = TextView(context).apply {
            text = "💃 Sala Just Dance (Bs / hora):"
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 12, 0, 0)
        }
        val etJustDance = EditText(context).apply {
            hint = "Tarifa Just Dance (ej. 15.0)"
            setText("15.0")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }
        container.addView(tvJustDance)
        container.addView(etJustDance)

        // Contenedor dinámico para secciones personalizadas
        val llSeccionesDinamicas = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(llSeccionesDinamicas)

        val mapaInputsDinamicos = mutableMapOf<String, Pair<SeccionSala, EditText>>()

        // Cargar valores actuales de Firebase (config_precios y secciones_salas)
        database.child("secciones_salas").get().addOnSuccessListener { snapSec ->
            database.child("config_precios").get().addOnSuccessListener { snapPrecios ->
                val pMinicine = snapPrecios.child("precio_minicine").getValue(Double::class.java)
                    ?: snapPrecios.child("precio_minicine").getValue(Long::class.java)?.toDouble() ?: 35.0
                val pVolantes = snapPrecios.child("precio_volantes").getValue(Double::class.java)
                    ?: snapPrecios.child("precio_volantes").getValue(Long::class.java)?.toDouble() ?: 15.0
                val pJustDance = snapPrecios.child("precio_just_dance").getValue(Double::class.java)
                    ?: snapPrecios.child("precio_just_dance").getValue(Long::class.java)?.toDouble() ?: 15.0

                etMinicine.setText(pMinicine.toString())
                etVolantes.setText(pVolantes.toString())
                etJustDance.setText(pJustDance.toString())

                llSeccionesDinamicas.removeAllViews()
                mapaInputsDinamicos.clear()

                for (child in snapSec.children) {
                    val sec = child.getValue(SeccionSala::class.java) ?: continue
                    if (sec.id.isBlank()) sec.id = child.key ?: ""
                    if (!sec.activa) continue
                    if (sec.id.lowercase() in listOf("minicine", "autitos", "just_dance")) continue

                    val precioGuardado = snapPrecios.child("precio_${sec.id}").getValue(Double::class.java)
                        ?: snapPrecios.child("precio_${sec.id}").getValue(Long::class.java)?.toDouble()
                        ?: sec.precioBaseDefault

                    val tvSec = TextView(context).apply {
                        text = "${sec.icono} ${sec.nombre} (Bs / hora):"
                        setTextColor(Color.parseColor("#DEFF9A"))
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(0, 12, 0, 0)
                    }
                    val etSec = EditText(context).apply {
                        hint = "Tarifa ${sec.nombre} (ej. ${sec.precioBaseDefault})"
                        setText(precioGuardado.toString())
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                        setTextColor(Color.WHITE)
                        setHintTextColor(Color.parseColor("#888888"))
                    }
                    llSeccionesDinamicas.addView(tvSec)
                    llSeccionesDinamicas.addView(etSec)
                    mapaInputsDinamicos[sec.id] = Pair(sec, etSec)
                }
            }
        }

        AlertDialog.Builder(context)
            .setTitle("💲 Precios Base de Salas")
            .setView(scrollView)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoMinicine = etMinicine.text.toString().toDoubleOrNull() ?: 35.0
                val nuevoVolantes = etVolantes.text.toString().toDoubleOrNull() ?: 15.0
                val nuevoJustDance = etJustDance.text.toString().toDoubleOrNull() ?: 15.0
                val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

                val updateMap = mutableMapOf<String, Any>(
                    "precio_minicine" to nuevoMinicine,
                    "precio_volantes" to nuevoVolantes,
                    "precio_just_dance" to nuevoJustDance,
                    "precio_autitos" to nuevoVolantes,
                    "precio_salas" to nuevoMinicine,
                    "ultima_actualizacion" to fecha,
                    "actualizado_por" to "Admin"
                )

                BasePriceConfig.precioMinicine = nuevoMinicine
                BasePriceConfig.precioVolantes = nuevoVolantes
                BasePriceConfig.precioJustDance = nuevoJustDance

                // Sincronizar secciones base en secciones_salas
                database.child("secciones_salas").child("minicine").child("precioBaseDefault").setValue(nuevoMinicine)
                database.child("secciones_salas").child("autitos").child("precioBaseDefault").setValue(nuevoVolantes)
                database.child("secciones_salas").child("just_dance").child("precioBaseDefault").setValue(nuevoJustDance)

                for ((secId, pair) in mapaInputsDinamicos) {
                    val (sec, et) = pair
                    val precioVal = et.text.toString().toDoubleOrNull() ?: sec.precioBaseDefault
                    updateMap["precio_$secId"] = precioVal
                    BasePriceConfig.preciosDinamicos[secId] = precioVal
                    BasePriceConfig.preciosDinamicos[sec.nombre] = precioVal
                    // Sincronizar también con la sección de sala en Firebase
                    database.child("secciones_salas").child(secId).child("precioBaseDefault").setValue(precioVal)
                }

                database.child("config_precios").updateChildren(updateMap).addOnSuccessListener {
                    Toast.makeText(context, "✅ Precios base actualizados y sincronizados", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    Toast.makeText(context, "❌ Error al guardar precios: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun mostrarDialogoCrearEditarProducto(
        codigoExistente: String? = null,
        nombreExistente: String = "",
        precioExistente: Double = 10.0,
        stockExistente: Int = 50,
        categoriaExistente: String = "Snacks",
        gramajeExistente: String = ""
    ) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.parseColor("#1A1C29"))
        }

        val etCodigo = EditText(context).apply {
            hint = "Código de Barras (ej. 77510001)"
            setText(codigoExistente ?: "")
            isEnabled = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        val etNombre = EditText(context).apply {
            hint = "Nombre del Producto (ej. Doritos)"
            setText(nombreExistente)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        val etGramaje = EditText(context).apply {
            hint = "Gramaje o Volumen (ej. 500ml, 120g - Opcional)"
            setText(gramajeExistente)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        val etPrecio = EditText(context).apply {
            hint = "Precio Venta en Bs"
            setText(precioExistente.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        val etStock = EditText(context).apply {
            hint = "Stock Actual (ej. 50 o +10 para sumar)"
            setText(stockExistente.toString())
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        // Fila de botones para incremento rápido de stock (+5, +10, +20, +50)
        val llStockButtons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 8)
        }
        listOf(5, 10, 20, 50).forEach { inc ->
            val btnInc = Button(context).apply {
                text = "+$inc"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#24283B"))
                setTextColor(Color.parseColor("#DEFF9A"))
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(2, 0, 2, 0)
                }
                layoutParams = lp
                setOnClickListener {
                    val currentTxt = etStock.text.toString().trim()
                    if (currentTxt.startsWith("+")) {
                        val curInc = currentTxt.removePrefix("+").trim().toIntOrNull() ?: 0
                        etStock.setText("+${curInc + inc}")
                    } else {
                        val curVal = currentTxt.toIntOrNull() ?: stockExistente
                        etStock.setText((curVal + inc).toString())
                    }
                }
            }
            llStockButtons.addView(btnInc)
        }

        val tvCatLabel = TextView(context).apply {
            text = "Categoría:"
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 12f
            setPadding(0, 8, 0, 4)
        }

        val categoriasFijas = arrayOf("Snack", "Bebidas", "Golosinas")
        val spinnerCategoria = Spinner(context).apply {
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, categoriasFijas)
            setAdapter(adapter)
            val index = when {
                categoriaExistente.startsWith("Snack", ignoreCase = true) -> 0
                categoriaExistente.equals("Bebidas", ignoreCase = true) -> 1
                categoriaExistente.equals("Golosinas", ignoreCase = true) -> 2
                else -> 0
            }
            setSelection(index)
            setBackgroundColor(Color.parseColor("#24283B"))
        }

        container.addView(etCodigo)
        container.addView(etNombre)
        container.addView(etGramaje)
        container.addView(etPrecio)
        container.addView(etStock)
        container.addView(llStockButtons)
        container.addView(tvCatLabel)
        container.addView(spinnerCategoria)

        val dialogBuilder = AlertDialog.Builder(context)
            .setTitle(if (codigoExistente == null) "➕ Nuevo Producto" else "✏️ Editar $nombreExistente")
            .setView(container)
            .setPositiveButton("Guardar") { _, _ ->
                val code = etCodigo.text.toString().trim()
                val name = etNombre.text.toString().trim()
                val gramaje = etGramaje.text.toString().trim()
                val price = etPrecio.text.toString().toDoubleOrNull() ?: 10.0
                val rawStock = etStock.text.toString().trim()
                val stock = if (rawStock.startsWith("+")) {
                    val inc = rawStock.removePrefix("+").trim().toIntOrNull() ?: 0
                    stockExistente + inc
                } else {
                    rawStock.toIntOrNull() ?: stockExistente
                }
                val cat = spinnerCategoria.selectedItem?.toString() ?: "Snack"
                val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

                if (code.isNotEmpty() && name.isNotEmpty()) {
                    // Validar duplicados de código y nombre en Firebase
                    database.child("productos_catalogo").get().addOnSuccessListener { snap ->
                        val esNuevo = codigoExistente == null
                        val codigoCambio = codigoExistente != null && codigoExistente != code

                        // 1. Validar código duplicado en creación o si el código fue cambiado
                        if ((esNuevo || codigoCambio) && snap.hasChild(code)) {
                            Toast.makeText(context, "❌ Error: Ya existe un producto con el código de barras '$code'", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }

                        // 2. Validar nombre y gramaje duplicado (en creación o edición)
                        fun normalizeG(g: String) = g.trim().replace("\\s+".toRegex(), "").lowercase()
                        val currentGramaje = gramaje.trim()

                        val duplicateNameAndGramajeSnap = snap.children.find { child ->
                            val childCode = child.key ?: ""
                            val childName = child.child("nombre").getValue(String::class.java)?.trim() ?: ""
                            val childGramaje = child.child("gramaje").getValue(String::class.java)?.trim() ?: ""
                            childCode != (codigoExistente ?: code) &&
                                childName.equals(name, ignoreCase = true) &&
                                normalizeG(childGramaje) == normalizeG(currentGramaje)
                        }

                        if (duplicateNameAndGramajeSnap != null) {
                            val existingCode = duplicateNameAndGramajeSnap.key
                            val msg = if (currentGramaje.isNotEmpty()) {
                                "❌ Ya existe otro producto con el mismo nombre y gramaje '$name $currentGramaje' (Código: $existingCode)"
                            } else {
                                "❌ Ya existe otro producto con el nombre '$name'. Ingresa el gramaje/volumen para diferenciar esta presentación."
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }

                        // Si el código cambió, eliminar el código anterior de Firebase
                        if (codigoCambio) {
                            database.child("productos_catalogo").child(codigoExistente!!).removeValue()
                        }

                        val map = mapOf(
                            "codigo_barras" to code,
                            "nombre" to name,
                            "gramaje" to gramaje,
                            "precio_unitario" to price,
                            "stock_actual" to stock,
                            "categoria" to cat,
                            "activo" to true,
                            "fecha_actualizacion" to now
                        )
                        database.child("productos_catalogo").child(code).setValue(map)
                        
                        // Registro en Auditoría
                        productAuditManager.registrarAuditoriaProducto(
                            tipoOperacion = if (esNuevo) "CREACION" else if (codigoCambio) "MIGRACION_CODIGO" else "EDICION",
                            codigoBarras = code,
                            nombreProducto = name,
                            categoria = cat,
                            precioAnterior = if (esNuevo) 0.0 else precioExistente,
                            precioNuevo = price,
                            stockAnterior = if (esNuevo) 0 else stockExistente,
                            stockNuevo = stock,
                            usuarioNombre = "Admin",
                            usuarioRol = "Administrador",
                            detalles = if (esNuevo) "Producto creado por el Administrador" else if (codigoCambio) "Código migrado de $codigoExistente a $code" else "Precio/stock/gramaje actualizados"
                        )

                        Toast.makeText(context, "Producto '$name' guardado exitosamente", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener {
                        Toast.makeText(context, "Error al verificar duplicados: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Código y Nombre son obligatorios", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)

        if (codigoExistente != null) {
            dialogBuilder.setNeutralButton("🗑️ Eliminar") { _, _ ->
                database.child("productos_catalogo").child(codigoExistente).removeValue()
                productAuditManager.registrarAuditoriaProducto(
                    tipoOperacion = "ELIMINACION",
                    codigoBarras = codigoExistente,
                    nombreProducto = nombreExistente,
                    categoria = categoriaExistente,
                    precioAnterior = precioExistente,
                    precioNuevo = 0.0,
                    stockAnterior = stockExistente,
                    stockNuevo = 0,
                    usuarioNombre = "Admin",
                    usuarioRol = "Administrador",
                    detalles = "Producto eliminado del catálogo"
                )
                Toast.makeText(context, "Producto eliminado", Toast.LENGTH_SHORT).show()
            }
        }

        dialogBuilder.show()
    }
}

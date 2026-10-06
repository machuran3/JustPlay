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

    fun mostrarDialogoGestionPOS() {
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
        }
        val tvTitulo = TextView(context).apply {
            text = "🖥️ Gestión de Terminales POS & Seguridad"
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

        // 1. TARJETA DE VERSIÓN MÍNIMA OBLIGATORIA (Force Update)
        val cardVersion = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
            setBackgroundColor(Color.parseColor("#24283B"))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 16, 0, 16)
            layoutParams = lp
        }

        val tvSubVersion = TextView(context).apply {
            text = "🛡️ Control de Versión Mínima Obligatoria (Force Update)"
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        }

        val tvDescVersion = TextView(context).apply {
            text = "Bloquea terminales con versiones obsoletas con error HTTP 426 (Upgrade Required)."
            setTextColor(Color.parseColor("#A0A0A0"))
            textSize = 11f
            setPadding(0, 4, 0, 8)
        }

        val tvVersionActual = TextView(context).apply {
            text = "Versión mínima requerida: Consultando..."
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
        }

        val btnConfigVersion = Button(context).apply {
            text = "⚙️ Cambiar Versión Mínima"
            setBackgroundColor(Color.parseColor("#1A1C29"))
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 11f
            setOnClickListener {
                mostrarDialogoCambiarVersionMinima()
            }
        }

        val versionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val minVer = snapshot.getValue(String::class.java) ?: "1.0.0"
                tvVersionActual.text = "Versión mínima requerida actual: v$minVer"
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.child("config_pos").child("min_app_version_desktop").addValueEventListener(versionListener)

        cardVersion.addView(tvSubVersion)
        cardVersion.addView(tvDescVersion)
        cardVersion.addView(tvVersionActual)
        cardVersion.addView(btnConfigVersion)
        container.addView(cardVersion)

        // 2. TARJETA MASTER SWITCH DE HABILITACIÓN POS
        val cardPermiso = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
            setBackgroundColor(Color.parseColor("#24283B"))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 16)
            layoutParams = lp
        }

        val tvSubPermiso = TextView(context).apply {
            text = "🔐 Interruptor Maestro de Terminales POS"
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        }

        val switchPermiso = SwitchCompat(context).apply {
            text = "Permitir ventas en todas las PC:"
            setTextColor(Color.WHITE)
            textSize = 13f
        }

        val tvEstadoPermiso = TextView(context).apply {
            text = "Consultando estado..."
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 4, 0, 0)
        }

        val permisoListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val habilitado = snapshot.getValue(Boolean::class.java) ?: true
                switchPermiso.isChecked = habilitado
                if (habilitado) {
                    tvEstadoPermiso.text = "✅ SERVICIO POS ACTIVO"
                    tvEstadoPermiso.setTextColor(Color.parseColor("#00E676"))
                } else {
                    tvEstadoPermiso.text = "⛔ SERVICIO POS SUSPENDIDO GLOBALMENTE"
                    tvEstadoPermiso.setTextColor(Color.parseColor("#FF5252"))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        database.child("config_pos").child("habilitado").addValueEventListener(permisoListener)

        switchPermiso.setOnCheckedChangeListener { _, isChecked ->
            val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            database.child("config_pos").child("habilitado").setValue(isChecked)
            database.child("config_pos").child("pos_habilitado").setValue(isChecked)
            database.child("config_pos").child("ultima_modificacion").setValue(fecha)
            database.child("config_pos").child("modificado_por").setValue("Admin JustPlay (${DeviceInfoProvider.getDeviceModel()})")
            
            // Registro de auditoría de seguridad
            val auditKey = database.child("auditoria_seguridad").push().key ?: ""
            if (auditKey.isNotEmpty()) {
                val auditData = mapOf(
                    "id" to auditKey,
                    "evento" to if (isChecked) "HABILITAR_POS_DESKTOP" else "DESACTIVAR_POS_DESKTOP",
                    "detalles" to if (isChecked) "Admin activó el servicio POS Desktop" else "Admin suspendió remotamente el servicio POS Desktop",
                    "usuario" to "Administrador",
                    "dispositivo" to DeviceInfoProvider.getDeviceModel(),
                    "fechaHora" to fecha,
                    "timestamp" to System.currentTimeMillis()
                )
                database.child("auditoria_seguridad").child(auditKey).setValue(auditData)
            }
            
            Toast.makeText(context, if (isChecked) "✅ Terminales POS Habilitadas" else "⛔ Terminales POS Desactivadas Remotamente", Toast.LENGTH_SHORT).show()
        }

        cardPermiso.addView(tvSubPermiso)
        cardPermiso.addView(switchPermiso)
        cardPermiso.addView(tvEstadoPermiso)
        container.addView(cardPermiso)

        // 3. TARJETA DE DISPOSITIVOS POS VINCULADOS (Device Management & Heartbeat)
        val tvHeaderDevices = TextView(context).apply {
            text = "💻 Terminales POS Vinculadas (Heartbeat & Sesión)"
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 8, 0, 8)
        }
        container.addView(tvHeaderDevices)

        val llListaDispositivos = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 16)
        }
        container.addView(llListaDispositivos)

        val devicesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                llListaDispositivos.removeAllViews()
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    val tvVacio = TextView(context).apply {
                        text = "No hay computadoras registradas aún.\nAl iniciar JustPlay POS en la PC se registrará automáticamente."
                        setTextColor(Color.parseColor("#888888"))
                        textSize = 12f
                        setPadding(12, 12, 12, 12)
                    }
                    llListaDispositivos.addView(tvVacio)
                    return
                }

                for (snap in snapshot.children) {
                    val devId = snap.child("deviceId").getValue(String::class.java) ?: snap.key ?: ""
                    val hwUuid = snap.child("hardwareUuid").getValue(String::class.java) ?: ""
                    val nombre = snap.child("nombre").getValue(String::class.java) ?: "PC Terminal"
                    val hostname = snap.child("hostname").getValue(String::class.java) ?: ""
                    val osName = snap.child("osName").getValue(String::class.java) ?: ""
                    val token = snap.child("sessionToken").getValue(String::class.java) ?: ""
                    val auth = snap.child("autorizado").getValue(Boolean::class.java) ?: false
                    val estado = snap.child("estadoConexion").getValue(String::class.java) ?: "OFFLINE"
                    val lastHb = snap.child("lastHeartbeat").getValue(Long::class.java) ?: 0L
                    val lastHbStr = snap.child("lastHeartbeatStr").getValue(String::class.java) ?: ""
                    val appVer = snap.child("appVersion").getValue(String::class.java) ?: "1.0.0"
                    val ip = snap.child("ipAddress").getValue(String::class.java) ?: ""
                    val fechaVinc = snap.child("fechaVinculacion").getValue(String::class.java) ?: ""

                    val terminal = TerminalPos(
                        deviceId = devId,
                        hardwareUuid = hwUuid,
                        nombre = nombre,
                        hostname = hostname,
                        osName = osName,
                        sessionToken = token,
                        autorizado = auth,
                        estadoConexion = estado,
                        lastHeartbeat = lastHb,
                        lastHeartbeatStr = lastHbStr,
                        appVersion = appVer,
                        ipAddress = ip,
                        fechaVinculacion = fechaVinc
                    )

                    val card = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16, 14, 16, 14)
                        setBackgroundColor(Color.parseColor("#24283B"))
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.setMargins(0, 0, 0, 10)
                        layoutParams = lp
                    }

                    val headerDev = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }

                    val tvNombrePc = TextView(context).apply {
                        text = "🖥️ $nombre"
                        setTextColor(Color.WHITE)
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val isOnline = terminal.isCurrentlyOnline()
                    val badgeEstado = TextView(context).apply {
                        if (!terminal.autorizado || terminal.estadoConexion == "DESVINCULADO") {
                            text = "⛔ DESVINCULADA"
                            setTextColor(Color.parseColor("#FF5252"))
                        } else if (isOnline) {
                            text = "🟢 ONLINE"
                            setTextColor(Color.parseColor("#00E676"))
                        } else {
                            text = "🔴 OFFLINE"
                            setTextColor(Color.parseColor("#FFA726"))
                        }
                        textSize = 11f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(8, 2, 8, 2)
                    }

                    headerDev.addView(tvNombrePc)
                    headerDev.addView(badgeEstado)
                    card.addView(headerDev)

                    val diffMin = if (lastHb > 0) (System.currentTimeMillis() - lastHb) / 60000 else -1
                    val lastSeenText = when {
                        isOnline -> "En línea y sincronizando (Pulso activo)"
                        diffMin >= 0 -> "Último pulso: hace $diffMin min ($lastHbStr)"
                        else -> "Sin registro de pulso"
                    }

                    val tvDetalles = TextView(context).apply {
                        val tokenPreview = if (token.length > 8) "${token.take(8)}..." else if (token.isEmpty()) "Invalido / Revocado" else token
                        text = "• SO: $osName | Host: $hostname\n• App: v$appVer | IP: ${if (ip.isNotEmpty()) ip else "Local"}\n• ID: $devId\n• Token Sesión: $tokenPreview\n• $lastSeenText"
                        setTextColor(Color.parseColor("#B0B3C6"))
                        textSize = 11f
                        setPadding(0, 4, 0, 8)
                    }
                    card.addView(tvDetalles)

                    val btnRowDev = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }

                    if (terminal.autorizado && terminal.estadoConexion != "DESVINCULADO") {
                        val btnDesvincular = Button(context).apply {
                            text = "🔌 Desvincular"
                            setBackgroundColor(Color.parseColor("#FF5252"))
                            setTextColor(Color.WHITE)
                            textSize = 11f
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                                setMargins(0, 0, 6, 0)
                            }
                            setOnClickListener {
                                desvincularTerminal(terminal)
                            }
                        }
                        btnRowDev.addView(btnDesvincular)
                    } else {
                        val btnAutorizar = Button(context).apply {
                            text = "✅ Autorizar y Vincular"
                            setBackgroundColor(Color.parseColor("#00E676"))
                            setTextColor(Color.BLACK)
                            textSize = 11f
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                                setMargins(0, 0, 6, 0)
                            }
                            setOnClickListener {
                                autorizarTerminal(terminal)
                            }
                        }
                        btnRowDev.addView(btnAutorizar)
                    }

                    val btnEliminar = Button(context).apply {
                        text = "🗑️ Eliminar"
                        setBackgroundColor(Color.parseColor("#1A1C29"))
                        setTextColor(Color.parseColor("#FF8A80"))
                        textSize = 11f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            eliminarTerminal(terminal)
                        }
                    }
                    btnRowDev.addView(btnEliminar)
                    card.addView(btnRowDev)

                    llListaDispositivos.addView(card)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.child("terminales_pos").addValueEventListener(devicesListener)

        // 4. Catálogo de Productos & Inventario
        val tvHeaderCat = TextView(context).apply {
            text = "📦 Catálogo de Productos & Inventario"
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 10, 0, 10)
        }
        container.addView(tvHeaderCat)

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
                            mostrarDialogoCrearEditarProducto(barcode, nombre, precio, stock, categoria)
                        }
                    }

                    val info = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val tvNom = TextView(context).apply {
                        text = "📦 $nombre"
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
            database.child("config_pos").child("habilitado").removeEventListener(permisoListener)
            database.child("config_pos").child("min_app_version_desktop").removeEventListener(versionListener)
            database.child("terminales_pos").removeEventListener(devicesListener)
        }

        dialog.show()
    }

    private fun mostrarDialogoCambiarVersionMinima() {
        val etVersion = EditText(context).apply {
            hint = "Ej: 1.0.0 o 1.1.0"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        database.child("config_pos").child("min_app_version_desktop").get().addOnSuccessListener { snap ->
            val v = snap.getValue(String::class.java) ?: "1.0.0"
            etVersion.setText(v)
        }

        AlertDialog.Builder(context)
            .setTitle("🛡️ Establecer Versión Mínima (Force Update)")
            .setMessage("Las versiones de Desktop inferiores a esta versión serán bloqueadas automáticamente con error HTTP 426.")
            .setView(etVersion)
            .setPositiveButton("Guardar") { _, _ ->
                val newVer = etVersion.text.toString().trim()
                if (newVer.isNotEmpty()) {
                    database.child("config_pos").child("min_app_version_desktop").setValue(newVer)
                    Toast.makeText(context, "Versión mínima establecida en v$newVer", Toast.LENGTH_SHORT).show()
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

    fun mostrarDialogoCrearEditarProducto(
        codigoExistente: String? = null,
        nombreExistente: String = "",
        precioExistente: Double = 10.0,
        stockExistente: Int = 50,
        categoriaExistente: String = "Snacks"
    ) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.parseColor("#1A1C29"))
        }

        val etCodigo = EditText(context).apply {
            hint = "Código de Barras (ej. 77510001)"
            setText(codigoExistente ?: "")
            isEnabled = codigoExistente == null
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        val etNombre = EditText(context).apply {
            hint = "Nombre del Producto (ej. Coca Cola 500ml)"
            setText(nombreExistente)
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
            hint = "Stock Actual"
            setText(stockExistente.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }

        val tvCatLabel = TextView(context).apply {
            text = "Categoría:"
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 12f
            setPadding(0, 12, 0, 4)
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
        container.addView(etPrecio)
        container.addView(etStock)
        container.addView(tvCatLabel)
        container.addView(spinnerCategoria)

        val dialogBuilder = AlertDialog.Builder(context)
            .setTitle(if (codigoExistente == null) "➕ Nuevo Producto" else "✏️ Editar $nombreExistente")
            .setView(container)
            .setPositiveButton("Guardar") { _, _ ->
                val code = etCodigo.text.toString().trim()
                val name = etNombre.text.toString().trim()
                val price = etPrecio.text.toString().toDoubleOrNull() ?: 10.0
                val stock = etStock.text.toString().toIntOrNull() ?: 50
                val cat = spinnerCategoria.selectedItem?.toString() ?: "Snack"
                val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

                if (code.isNotEmpty() && name.isNotEmpty()) {
                    // Validar duplicados de código y nombre en Firebase
                    database.child("productos_catalogo").get().addOnSuccessListener { snap ->
                        val esNuevo = codigoExistente == null

                        // 1. Validar código duplicado en creación
                        if (esNuevo && snap.hasChild(code)) {
                            Toast.makeText(context, "❌ Error: Ya existe un producto con el código de barras '$code'", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }

                        // 2. Validar nombre duplicado (en creación o edición)
                        val duplicateNameSnap = snap.children.find { child ->
                            val childCode = child.key ?: ""
                            val childName = child.child("nombre").getValue(String::class.java)?.trim() ?: ""
                            childCode != (codigoExistente ?: code) && childName.equals(name, ignoreCase = true)
                        }

                        if (duplicateNameSnap != null) {
                            val existingCode = duplicateNameSnap.key
                            Toast.makeText(context, "❌ Error: Ya existe otro producto con el nombre '$name' (Código: $existingCode)", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }

                        val map = mapOf(
                            "codigo_barras" to code,
                            "nombre" to name,
                            "precio_unitario" to price,
                            "stock_actual" to stock,
                            "categoria" to cat,
                            "activo" to true,
                            "fecha_actualizacion" to now
                        )
                        database.child("productos_catalogo").child(code).setValue(map)
                        
                        // Registro en Auditoría
                        productAuditManager.registrarAuditoriaProducto(
                            tipoOperacion = if (esNuevo) "CREACION" else "EDICION",
                            codigoBarras = code,
                            nombreProducto = name,
                            categoria = cat,
                            precioAnterior = if (esNuevo) 0.0 else precioExistente,
                            precioNuevo = price,
                            stockAnterior = if (esNuevo) 0 else stockExistente,
                            stockNuevo = stock,
                            usuarioNombre = "Admin",
                            usuarioRol = "Administrador",
                            detalles = if (esNuevo) "Producto creado por el Administrador" else "Precio/stock actualizados"
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

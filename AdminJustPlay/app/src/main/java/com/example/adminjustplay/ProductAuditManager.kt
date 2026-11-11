package com.example.adminjustplay

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modelo inmutable para auditoría de operaciones sobre el catálogo de productos.
 */
data class ProductAuditEvent(
    val id: String = "",
    val tipoOperacion: String = "", // "CREACION", "EDICION", "ELIMINACION", "AJUSTE_STOCK"
    val codigoBarras: String = "",
    val nombreProducto: String = "",
    val categoria: String = "",
    val precioAnterior: Double = 0.0,
    val precioNuevo: Double = 0.0,
    val stockAnterior: Int = 0,
    val stockNuevo: Int = 0,
    val usuarioNombre: String = "",
    val usuarioRol: String = "",
    val dispositivoModelo: String = "",
    val fechaHora: String = "",
    val timestamp: Long = 0L,
    val detalles: String = ""
)

/**
 * Single Responsibility Principle (SRP):
 * Administra el registro y consulta de la bitácora de auditoría de productos.
 */
class ProductAuditManager(
    private val context: Context,
    private val database: DatabaseReference
) {

    fun registrarAuditoriaProducto(
        tipoOperacion: String,
        codigoBarras: String,
        nombreProducto: String,
        categoria: String = "General",
        precioAnterior: Double = 0.0,
        precioNuevo: Double = 0.0,
        stockAnterior: Int = 0,
        stockNuevo: Int = 0,
        usuarioNombre: String = "Admin",
        usuarioRol: String = "Administrador",
        detalles: String = ""
    ) {
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val timestamp = System.currentTimeMillis()
        val dispositivoModelo = DeviceInfoProvider.getDeviceModel()

        val logId = database.child("auditoria_productos").push().key ?: return
        val logData = mapOf(
            "id" to logId,
            "tipo_operacion" to tipoOperacion,
            "codigo_barras" to codigoBarras,
            "nombre_producto" to nombreProducto,
            "categoria" to categoria,
            "precio_anterior" to precioAnterior,
            "precio_nuevo" to precioNuevo,
            "stock_anterior" to stockAnterior,
            "stock_nuevo" to stockNuevo,
            "usuario_nombre" to usuarioNombre,
            "usuario_rol" to usuarioRol,
            "dispositivo_modelo" to dispositivoModelo,
            "fecha_hora" to now,
            "timestamp" to timestamp,
            "detalles" to detalles
        )

        database.child("auditoria_productos").child(logId).setValue(logData)
    }

    fun mostrarDialogoHistorialAuditoriaProductos() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_historial_auditoria, null)
        val tvTitulo = dialogView.findViewById<TextView>(R.id.tvDialogAuditTitulo)
        val tvContador = dialogView.findViewById<TextView>(R.id.tvDialogAuditContador)
        val tvSub = dialogView.findViewById<TextView>(R.id.tvDialogAuditSubtitulo)
        val btnCerrar = dialogView.findViewById<TextView>(R.id.btnDialogAuditCerrar)
        val etBuscar = dialogView.findViewById<EditText>(R.id.etDialogAuditBuscar)
        val llContenedor = dialogView.findViewById<LinearLayout>(R.id.llDialogAuditContenedor)
        val tvEstado = dialogView.findViewById<TextView>(R.id.tvDialogAuditEstado)

        tvTitulo.text = "📦 Auditoría de Catálogo & Precios"
        tvSub.text = "Registro forense de quién (Empleado / Admin) creó, modificó precios, ajustó stock o eliminó productos."

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCerrar.setOnClickListener { dialog.dismiss() }

        val todosLosItems = mutableListOf<ProductAuditEvent>()

        fun renderizarEventos(lista: List<ProductAuditEvent>) {
            llContenedor.removeAllViews()
            tvContador.text = "${lista.size} eventos"

            if (lista.isEmpty()) {
                val tvVacio = TextView(context).apply {
                    text = "No se encontraron registros de cambios de productos."
                    setTextColor(Color.parseColor("#888888"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, 24, 0, 24)
                }
                llContenedor.addView(tvVacio)
                return
            }

            for (ev in lista) {
                val itemView = LayoutInflater.from(context).inflate(R.layout.item_auditoria, llContenedor, false)

                val tvFecha = itemView.findViewById<TextView>(R.id.tvAuditFechaHora)
                val tvBadge = itemView.findViewById<TextView>(R.id.tvAuditBadgeAccion)
                val tvTituloItem = itemView.findViewById<TextView>(R.id.tvAuditTitulo)
                val tvResp = itemView.findViewById<TextView>(R.id.tvAuditResponsable)
                val tvDisp = itemView.findViewById<TextView>(R.id.tvAuditDispositivo)
                val tvDet = itemView.findViewById<TextView>(R.id.tvAuditDetalles)
                val tvHighlight = itemView.findViewById<TextView>(R.id.tvAuditHighlight)

                tvFecha.text = ev.fechaHora

                val (badgeColor, badgeIcon, badgeText) = when (ev.tipoOperacion.uppercase()) {
                    "CREACION" -> Triple("#00E676", "✨", "CREACIÓN")
                    "ELIMINACION" -> Triple("#FF5252", "🗑️", "ELIMINACIÓN")
                    "EDICION", "MODIFICACION" -> Triple("#FFB300", "✏️", "EDICIÓN")
                    "VENTA_DESCUENTO", "AJUSTE_STOCK" -> Triple("#00E5FF", "📦", "STOCK")
                    else -> Triple("#DEFF9A", "🏷️", ev.tipoOperacion)
                }
                tvBadge.text = "$badgeIcon $badgeText"
                tvBadge.setTextColor(Color.parseColor(badgeColor))

                val catTag = if (ev.categoria.isNotBlank() && ev.categoria != "General") " (${ev.categoria})" else ""
                tvTituloItem.text = "🍿 ${ev.nombreProducto}$catTag"

                tvResp.text = "👤 Autor: ${ev.usuarioNombre} (${ev.usuarioRol})"
                val dispStr = if (ev.dispositivoModelo.isNotBlank()) ev.dispositivoModelo else "Terminal POS / Móvil"
                tvDisp.text = "📱 Dispositivo: $dispStr"

                val barCodeStr = if (ev.codigoBarras.isNotBlank()) "Cód: ${ev.codigoBarras}" else ""
                val detStr = if (ev.detalles.isNotBlank()) " | ${ev.detalles}" else ""
                tvDet.text = "$barCodeStr$detStr".trimStart(' ', '|')

                // Comparación de cambios de precio y stock estilo registros
                val textoHighlight = if (ev.tipoOperacion.uppercase() in listOf("EDICION", "MODIFICACION")) {
                    "💰 ${ev.precioAnterior} Bs ➔ ${ev.precioNuevo} Bs  |  📦 Stock: ${ev.stockAnterior} ➔ ${ev.stockNuevo}"
                } else {
                    "💰 Precio: ${ev.precioNuevo} Bs  |  📦 Stock: ${ev.stockNuevo}"
                }
                tvHighlight.text = textoHighlight
                tvHighlight.setTextColor(Color.parseColor(if (ev.tipoOperacion.uppercase() == "ELIMINACION") "#FF5252" else "#00E676"))

                llContenedor.addView(itemView)
            }
        }

        etBuscar.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                if (q.isEmpty()) {
                    renderizarEventos(todosLosItems)
                } else {
                    val filtrados = todosLosItems.filter {
                        it.nombreProducto.lowercase().contains(q) ||
                                it.codigoBarras.lowercase().contains(q) ||
                                it.usuarioNombre.lowercase().contains(q) ||
                                it.tipoOperacion.lowercase().contains(q) ||
                                it.categoria.lowercase().contains(q) ||
                                it.fechaHora.lowercase().contains(q) ||
                                it.detalles.lowercase().contains(q)
                    }
                    renderizarEventos(filtrados)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                todosLosItems.clear()
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    tvEstado.text = "No hay registros de cambios de productos."
                    tvContador.text = "0 eventos"
                    return
                }

                for (snap in snapshot.children) {
                    val ev = ProductAuditEvent(
                        id = snap.child("id").getValue(String::class.java) ?: snap.key ?: "",
                        tipoOperacion = snap.child("tipo_operacion").getValue(String::class.java) ?: "",
                        codigoBarras = snap.child("codigo_barras").getValue(String::class.java) ?: "",
                        nombreProducto = snap.child("nombre_producto").getValue(String::class.java) ?: "",
                        categoria = snap.child("categoria").getValue(String::class.java) ?: "General",
                        precioAnterior = snap.child("precio_anterior").getValue(Double::class.java) ?: 0.0,
                        precioNuevo = snap.child("precio_nuevo").getValue(Double::class.java) ?: 0.0,
                        stockAnterior = snap.child("stock_anterior").getValue(Int::class.java) ?: 0,
                        stockNuevo = snap.child("stock_nuevo").getValue(Int::class.java) ?: 0,
                        usuarioNombre = snap.child("usuario_nombre").getValue(String::class.java) ?: "Desconocido",
                        usuarioRol = snap.child("usuario_rol").getValue(String::class.java) ?: "Empleado",
                        dispositivoModelo = snap.child("dispositivo_modelo").getValue(String::class.java) ?: "",
                        fechaHora = snap.child("fecha_hora").getValue(String::class.java) ?: "",
                        timestamp = snap.child("timestamp").getValue(Long::class.java) ?: 0L,
                        detalles = snap.child("detalles").getValue(String::class.java) ?: ""
                    )
                    todosLosItems.add(ev)
                }

                todosLosItems.sortByDescending { it.timestamp }

                val q = etBuscar.text?.toString()?.trim()?.lowercase() ?: ""
                if (q.isEmpty()) {
                    renderizarEventos(todosLosItems)
                } else {
                    val filtrados = todosLosItems.filter {
                        it.nombreProducto.lowercase().contains(q) ||
                                it.codigoBarras.lowercase().contains(q) ||
                                it.usuarioNombre.lowercase().contains(q) ||
                                it.tipoOperacion.lowercase().contains(q) ||
                                it.categoria.lowercase().contains(q) ||
                                it.fechaHora.lowercase().contains(q) ||
                                it.detalles.lowercase().contains(q)
                    }
                    renderizarEventos(filtrados)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                tvEstado.text = "Error al cargar historial: ${error.message}"
            }
        }

        database.child("auditoria_productos").limitToLast(100).addValueEventListener(listener)
        dialog.setOnDismissListener {
            database.child("auditoria_productos").removeEventListener(listener)
        }

        dialog.show()
    }
}

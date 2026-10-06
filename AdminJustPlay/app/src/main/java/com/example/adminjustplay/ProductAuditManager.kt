package com.example.adminjustplay

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
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
        val scrollView = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.parseColor("#1A1C29"))
        }
        scrollView.addView(container)

        val dialog = AlertDialog.Builder(context)
            .setView(scrollView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvTitulo = TextView(context).apply {
            text = "📜 Historial de Cambios en Productos"
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnCerrar = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#A0A0A0"))
            textSize = 20f
            setPadding(12, 0, 12, 0)
            setOnClickListener { dialog.dismiss() }
        }
        header.addView(tvTitulo)
        header.addView(btnCerrar)
        container.addView(header)

        val tvSub = TextView(context).apply {
            text = "Registro histórico de quién (Empleado / Admin) creó, modificó precios/stock o eliminó productos."
            setTextColor(Color.parseColor("#A0A0A0"))
            textSize = 12f
            setPadding(0, 6, 0, 16)
        }
        container.addView(tvSub)

        val llListaLogs = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(llListaLogs)

        val tvCargando = TextView(context).apply {
            text = "Cargando historial de cambios..."
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 13f
        }
        llListaLogs.addView(tvCargando)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                llListaLogs.removeAllViews()
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    val tvVacio = TextView(context).apply {
                        text = "No hay registros de cambios de productos."
                        setTextColor(Color.parseColor("#888888"))
                        textSize = 13f
                        setPadding(0, 10, 0, 10)
                    }
                    llListaLogs.addView(tvVacio)
                    return
                }

                val items = mutableListOf<ProductAuditEvent>()
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
                    items.add(ev)
                }

                items.sortByDescending { it.timestamp }

                for (ev in items) {
                    val card = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16, 14, 16, 14)
                        setBackgroundColor(Color.parseColor("#24283B"))
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.setMargins(0, 0, 0, 10)
                        layoutParams = lp
                    }

                    val colorOperacion = when (ev.tipoOperacion) {
                        "CREACION" -> "#00E676"
                        "ELIMINACION" -> "#FF5252"
                        "EDICION", "MODIFICACION" -> "#FFD700"
                        "VENTA_DESCUENTO" -> "#00E5FF"
                        else -> "#DEFF9A"
                    }

                    val tvOperacion = TextView(context).apply {
                        text = "🏷️ [${ev.tipoOperacion}] ${ev.nombreProducto} (Cód: ${ev.codigoBarras})"
                        setTextColor(Color.parseColor(colorOperacion))
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                    }

                    val tvCambio = TextView(context).apply {
                        text = if (ev.tipoOperacion == "EDICION") {
                            "Precio: ${ev.precioAnterior} Bs ➔ ${ev.precioNuevo} Bs | Stock: ${ev.stockAnterior} ➔ ${ev.stockNuevo}"
                        } else {
                            "Precio: ${ev.precioNuevo} Bs | Stock: ${ev.stockNuevo} | Cat: ${ev.categoria}"
                        }
                        setTextColor(Color.WHITE)
                        textSize = 12f
                    }

                    val tvAutor = TextView(context).apply {
                        text = "👤 Autor: ${ev.usuarioNombre} (${ev.usuarioRol}) | 📱 ${ev.dispositivoModelo}"
                        setTextColor(Color.parseColor("#DEFF9A"))
                        textSize = 11f
                    }

                    val tvFecha = TextView(context).apply {
                        text = "🕒 ${ev.fechaHora} ${if (ev.detalles.isNotEmpty()) "| " + ev.detalles else ""}"
                        setTextColor(Color.parseColor("#A0A0A0"))
                        textSize = 11f
                    }

                    card.addView(tvOperacion)
                    card.addView(tvCambio)
                    card.addView(tvAutor)
                    card.addView(tvFecha)
                    llListaLogs.addView(card)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        database.child("auditoria_productos").limitToLast(100).addValueEventListener(listener)
        dialog.setOnDismissListener {
            database.child("auditoria_productos").removeEventListener(listener)
        }

        dialog.show()
    }
}

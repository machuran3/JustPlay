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
 * Modelo de datos inmutable para eventos de auditoría de salas y dispositivos.
 */
data class RoomAuditEvent(
    val id: String = "",
    val tipoAccion: String = "",
    val salaId: String = "",
    val salaNombre: String = "",
    val categoria: String = "",
    val tiempoMinutos: Int = 0,
    val usuarioNombre: String = "",
    val usuarioRol: String = "",
    val dispositivoModelo: String = "",
    val dispositivoId: String = "",
    val fechaHora: String = "",
    val timestamp: Long = 0L,
    val detalles: String = ""
)

/**
 * Single Responsibility Principle (SRP):
 * Administra el registro y la visualización de la bitácora de auditoría de salas y dispositivos.
 */
class RoomAuditManager(
    private val context: Context,
    private val database: DatabaseReference
) {

    /**
     * Registra una acción crítica de sala en Firebase con trazabilidad de usuario y dispositivo.
     */
    fun registrarAccionSala(
        tipoAccion: String,
        salaId: String,
        salaNombre: String,
        categoria: String,
        tiempoMinutos: Int = 0,
        usuarioNombre: String = "Admin",
        usuarioRol: String = "Administrador",
        detalles: String = ""
    ) {
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val timestamp = System.currentTimeMillis()
        val dispositivoModelo = DeviceInfoProvider.getDeviceModel()
        val dispositivoId = DeviceInfoProvider.getDeviceId(context)

        val logId = database.child("auditoria_salas").push().key ?: return
        val logData = mapOf(
            "id" to logId,
            "tipo_accion" to tipoAccion,
            "sala_id" to salaId,
            "sala_nombre" to salaNombre,
            "categoria" to categoria,
            "tiempo_minutos" to tiempoMinutos,
            "usuario_nombre" to usuarioNombre,
            "usuario_rol" to usuarioRol,
            "dispositivo_modelo" to dispositivoModelo,
            "dispositivo_id" to dispositivoId,
            "fecha_hora" to now,
            "timestamp" to timestamp,
            "detalles" to detalles
        )

        database.child("auditoria_salas").child(logId).setValue(logData)
    }

    /**
     * Muestra la vista de bitácora de auditoría detallada de salas e identidad de dispositivos.
     */
    fun mostrarDialogoHistorialAuditoriaSalas() {
        mostrarDialogoHistorialAuditoriaPorArea(null, "🛡️ Trazabilidad de Salas & Dispositivos")
    }

    /**
     * Muestra la vista de bitácora de auditoría filtrada por área específica (Cine, Volantes, Just Dance).
     */
    fun mostrarDialogoHistorialAuditoriaPorArea(categoriaFiltro: String?, tituloArea: String) {
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

        // Header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvTitulo = TextView(context).apply {
            text = tituloArea
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
            text = if (categoriaFiltro != null) {
                "Historial forense exclusivo del área $categoriaFiltro: usuario, dispositivo, tiempo y cambios."
            } else {
                "Registro forense global de quién y desde qué celular/dispositivo se operaron las salas."
            }
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
            text = "Cargando bitácora de auditoría..."
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 13f
        }
        llListaLogs.addView(tvCargando)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                llListaLogs.removeAllViews()
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    val tvVacio = TextView(context).apply {
                        text = "No hay registros de auditoría disponibles para esta sección."
                        setTextColor(Color.parseColor("#888888"))
                        textSize = 13f
                        setPadding(0, 10, 0, 10)
                    }
                    llListaLogs.addView(tvVacio)
                    return
                }

                val items = mutableListOf<RoomAuditEvent>()
                for (snap in snapshot.children) {
                    val cat = snap.child("categoria").getValue(String::class.java) ?: ""
                    val sId = snap.child("sala_id").getValue(String::class.java) ?: ""
                    val sIdInt = sId.toIntOrNull() ?: 0

                    // Filtrar por categoría si se especificó
                    if (categoriaFiltro != null) {
                        val coincide = when (categoriaFiltro) {
                            "Minicine" -> cat.equals("Minicine", ignoreCase = true) || (sIdInt in 1..99)
                            "Autitos" -> cat.equals("Autitos", ignoreCase = true) || (sIdInt in 100..199)
                            "Just Dance" -> cat.equals("Just Dance", ignoreCase = true) || (sIdInt >= 200)
                            else -> cat.contains(categoriaFiltro, ignoreCase = true)
                        }
                        if (!coincide) continue
                    }

                    val ev = RoomAuditEvent(
                        id = snap.child("id").getValue(String::class.java) ?: snap.key ?: "",
                        tipoAccion = snap.child("tipo_accion").getValue(String::class.java) ?: "",
                        salaId = sId,
                        salaNombre = snap.child("sala_nombre").getValue(String::class.java) ?: "",
                        categoria = cat,
                        tiempoMinutos = snap.child("tiempo_minutos").getValue(Int::class.java) ?: 0,
                        usuarioNombre = snap.child("usuario_nombre").getValue(String::class.java) ?: "Desconocido",
                        usuarioRol = snap.child("usuario_rol").getValue(String::class.java) ?: "Empleado",
                        dispositivoModelo = snap.child("dispositivo_modelo").getValue(String::class.java) ?: "Dispositivo Móvil",
                        dispositivoId = snap.child("dispositivo_id").getValue(String::class.java) ?: "",
                        fechaHora = snap.child("fecha_hora").getValue(String::class.java) ?: "",
                        timestamp = snap.child("timestamp").getValue(Long::class.java) ?: 0L,
                        detalles = snap.child("detalles").getValue(String::class.java) ?: ""
                    )
                    items.add(ev)
                }

                if (items.isEmpty()) {
                    val tvVacio = TextView(context).apply {
                        text = "No se encontraron eventos para $tituloArea."
                        setTextColor(Color.parseColor("#888888"))
                        textSize = 13f
                        setPadding(0, 10, 0, 10)
                    }
                    llListaLogs.addView(tvVacio)
                    return
                }

                // Ordenar cronológicamente descendente (más recientes primero)
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

                    val badgeColor = when (ev.tipoAccion) {
                        "INICIO_TIEMPO", "INICIAR" -> "#00E676"
                        "ELIMINACION", "ELIMINAR" -> "#FF5252"
                        "PAUSAR", "PAUSA" -> "#FFD700"
                        "AGREGAR_TIEMPO" -> "#00E5FF"
                        "MODIFICACION", "EDICION" -> "#FFB300"
                        else -> "#DEFF9A"
                    }

                    val tvAccion = TextView(context).apply {
                        text = "▶ ${ev.tipoAccion}: ${ev.salaNombre} ${if (ev.categoria.isNotEmpty()) "(${ev.categoria})" else ""}"
                        setTextColor(Color.parseColor(badgeColor))
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                    }

                    val tvUsuario = TextView(context).apply {
                        text = "👤 Responsable: ${ev.usuarioNombre} (${ev.usuarioRol})"
                        setTextColor(Color.WHITE)
                        textSize = 12f
                    }

                    val tvDispositivo = TextView(context).apply {
                        text = "📱 Dispositivo: ${ev.dispositivoModelo} (ID: ${ev.dispositivoId})"
                        setTextColor(Color.parseColor("#DEFF9A"))
                        textSize = 11f
                        setTypeface(null, Typeface.BOLD)
                    }

                    val tvFecha = TextView(context).apply {
                        text = "🕒 Fecha: ${ev.fechaHora} ${if (ev.detalles.isNotEmpty()) "| " + ev.detalles else ""}"
                        setTextColor(Color.parseColor("#A0A0A0"))
                        textSize = 11f
                    }

                    card.addView(tvAccion)
                    card.addView(tvUsuario)
                    card.addView(tvDispositivo)
                    card.addView(tvFecha)
                    llListaLogs.addView(card)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        database.child("auditoria_salas").limitToLast(150).addValueEventListener(listener)
        dialog.setOnDismissListener {
            database.child("auditoria_salas").removeEventListener(listener)
        }

        dialog.show()
    }
}

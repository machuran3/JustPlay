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
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_historial_auditoria, null)
        val tvTitulo = dialogView.findViewById<TextView>(R.id.tvDialogAuditTitulo)
        val tvContador = dialogView.findViewById<TextView>(R.id.tvDialogAuditContador)
        val tvSub = dialogView.findViewById<TextView>(R.id.tvDialogAuditSubtitulo)
        val btnCerrar = dialogView.findViewById<TextView>(R.id.btnDialogAuditCerrar)
        val etBuscar = dialogView.findViewById<EditText>(R.id.etDialogAuditBuscar)
        val llContenedor = dialogView.findViewById<LinearLayout>(R.id.llDialogAuditContenedor)
        val tvEstado = dialogView.findViewById<TextView>(R.id.tvDialogAuditEstado)

        tvTitulo.text = tituloArea
        tvSub.text = if (categoriaFiltro != null) {
            "Historial forense exclusivo del área $categoriaFiltro: quién, cuándo y desde qué dispositivo se operó."
        } else {
            "Registro forense global de quién y desde qué celular/dispositivo se operaron las salas."
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCerrar.setOnClickListener { dialog.dismiss() }

        val todosLosItems = mutableListOf<RoomAuditEvent>()

        fun renderizarEventos(lista: List<RoomAuditEvent>) {
            llContenedor.removeAllViews()
            tvContador.text = "${lista.size} eventos"

            if (lista.isEmpty()) {
                val tvVacio = TextView(context).apply {
                    text = "No se encontraron eventos para el criterio de búsqueda."
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

                // Badge de acción con colores semánticos
                val (badgeColor, badgeIcon) = when (ev.tipoAccion.uppercase()) {
                    "INICIO_TIEMPO", "INICIAR" -> Pair("#00E676", "▶")
                    "ELIMINACION", "ELIMINAR", "LIBERAR", "FINALIZAR" -> Pair("#FF5252", "🛑")
                    "PAUSAR", "PAUSA" -> Pair("#FFD700", "⏸️")
                    "AGREGAR_TIEMPO" -> Pair("#00E5FF", "⏱️")
                    "MODIFICACION", "EDICION" -> Pair("#FFB300", "✏️")
                    else -> Pair("#DEFF9A", "📌")
                }
                tvBadge.text = "$badgeIcon ${ev.tipoAccion}"
                tvBadge.setTextColor(Color.parseColor(badgeColor))

                // Icono según categoría
                val iconoCat = when {
                    ev.categoria.contains("Cine", ignoreCase = true) || ev.categoria.contains("Minicine", ignoreCase = true) -> "🎬"
                    ev.categoria.contains("Auto", ignoreCase = true) || ev.categoria.contains("Volante", ignoreCase = true) -> "🏎️"
                    ev.categoria.contains("Dance", ignoreCase = true) -> "💃"
                    else -> "🛋️"
                }
                val catNombre = if (ev.categoria.isNotBlank()) "(${ev.categoria})" else ""
                tvTituloItem.text = "$iconoCat ${ev.salaNombre} $catNombre"

                tvResp.text = "👤 Responsable: ${ev.usuarioNombre} (${ev.usuarioRol})"
                val dispId = if (ev.dispositivoId.isNotBlank()) " | ID: ${ev.dispositivoId}" else ""
                tvDisp.text = "📱 Dispositivo: ${ev.dispositivoModelo}$dispId"

                val tiempoStr = if (ev.tiempoMinutos > 0) "⏱️ Tiempo: ${ev.tiempoMinutos} min" else "⏱️ Tiempo: Libre"
                val extraDet = if (ev.detalles.isNotBlank()) " | ${ev.detalles}" else ""
                tvDet.text = "$tiempoStr$extraDet"

                // Resaltado de acción
                tvHighlight.text = "⚡ Acción registrada con trazabilidad de seguridad"
                tvHighlight.setTextColor(Color.parseColor(badgeColor))

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
                        it.salaNombre.lowercase().contains(q) ||
                                it.usuarioNombre.lowercase().contains(q) ||
                                it.tipoAccion.lowercase().contains(q) ||
                                it.dispositivoModelo.lowercase().contains(q) ||
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
                    tvEstado.text = "No hay registros de auditoría disponibles para esta sección."
                    tvContador.text = "0 eventos"
                    return
                }

                for (snap in snapshot.children) {
                    val cat = snap.child("categoria").getValue(String::class.java) ?: ""
                    val sId = snap.child("sala_id").getValue(String::class.java) ?: ""
                    val sIdInt = sId.toIntOrNull() ?: 0

                    if (categoriaFiltro != null) {
                        val coincide = when (categoriaFiltro) {
                            "Minicine", "CINE" -> cat.equals("Minicine", ignoreCase = true) || (sIdInt in 1..99)
                            "Autitos", "AUTITOS" -> cat.equals("Autitos", ignoreCase = true) || (sIdInt in 100..199)
                            "Just Dance", "JUST_DANCE" -> cat.equals("Just Dance", ignoreCase = true) || (sIdInt >= 200)
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
                    todosLosItems.add(ev)
                }

                todosLosItems.sortByDescending { it.timestamp }

                val q = etBuscar.text?.toString()?.trim()?.lowercase() ?: ""
                if (q.isEmpty()) {
                    renderizarEventos(todosLosItems)
                } else {
                    val filtrados = todosLosItems.filter {
                        it.salaNombre.lowercase().contains(q) ||
                                it.usuarioNombre.lowercase().contains(q) ||
                                it.tipoAccion.lowercase().contains(q) ||
                                it.dispositivoModelo.lowercase().contains(q) ||
                                it.fechaHora.lowercase().contains(q) ||
                                it.detalles.lowercase().contains(q)
                    }
                    renderizarEventos(filtrados)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                tvEstado.text = "Error al cargar la bitácora: ${error.message}"
            }
        }

        database.child("auditoria_salas").limitToLast(150).addValueEventListener(listener)
        dialog.setOnDismissListener {
            database.child("auditoria_salas").removeEventListener(listener)
        }

        dialog.show()
    }
}

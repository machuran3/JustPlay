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
    /**
     * Muestra la vista de bitácora de auditoría filtrada por área específica (Cine, Volantes, Just Dance).
     * Rediseñado para mostrar registros inmutables con diseño limpio idéntico a Registros y búsqueda por cliente/celular.
     */
    fun mostrarDialogoHistorialAuditoriaPorArea(categoriaFiltro: String?, tituloArea: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_historial_auditoria, null)
        val tvTitulo = dialogView.findViewById<TextView>(R.id.tvDialogAuditTitulo)
        val tvContador = dialogView.findViewById<TextView>(R.id.tvDialogAuditContador)
        val tvSub = dialogView.findViewById<TextView>(R.id.tvDialogAuditSubtitulo)
        val tvResumen = dialogView.findViewById<TextView>(R.id.tvDialogAuditResumen)
        val btnCerrar = dialogView.findViewById<TextView>(R.id.btnDialogAuditCerrar)
        val etBuscar = dialogView.findViewById<EditText>(R.id.etDialogAuditBuscar)
        val llContenedor = dialogView.findViewById<LinearLayout>(R.id.llDialogAuditContenedor)
        val tvEstado = dialogView.findViewById<TextView>(R.id.tvDialogAuditEstado)

        tvTitulo.text = tituloArea
        tvSub.text = "Auditoría forense e inmutable: clientes, celulares, importes cobrados y registros activos/borrados."
        tvResumen.visibility = View.VISIBLE

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCerrar.setOnClickListener { dialog.dismiss() }

        val todosLosRegistros = mutableListOf<RegistroEntry>()

        fun renderizarRegistros(lista: List<RegistroEntry>) {
            llContenedor.removeAllViews()
            tvContador.text = "${lista.size} registros"

            val totalActivos = lista.filter { !it.eliminado }.sumOf { it.totalCobrado }
            val countBorrados = lista.count { it.eliminado }
            val totalActivosFmt = if (totalActivos % 1.0 == 0.0) totalActivos.toInt().toString() else totalActivos.toString()
            tvResumen.text = "📊 ${lista.size} registros (Borrados: $countBorrados) • Total activo: $totalActivosFmt Bs"

            if (lista.isEmpty()) {
                val tvVacio = TextView(context).apply {
                    text = "No se encontraron registros de auditoría para el criterio de búsqueda."
                    setTextColor(Color.parseColor("#888888"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, 30, 0, 30)
                }
                llContenedor.addView(tvVacio)
                return
            }

            for (reg in lista) {
                val itemView = LayoutInflater.from(context).inflate(R.layout.item_registro_admin, llContenedor, false)

                val cardView = itemView.findViewById<androidx.cardview.widget.CardView>(R.id.cardRegistroAdmin)
                val tvRegFechaHora = itemView.findViewById<TextView>(R.id.tvRegFechaHora)
                val tvRegMetodoPago = itemView.findViewById<TextView>(R.id.tvRegMetodoPago)
                val tvRegTitulo = itemView.findViewById<TextView>(R.id.tvRegTitulo)
                val tvRegCliente = itemView.findViewById<TextView>(R.id.tvRegCliente)
                val tvRegDetalles = itemView.findViewById<TextView>(R.id.tvRegDetalles)
                val tvRegVolantes = itemView.findViewById<TextView>(R.id.tvRegVolantes)
                val tvRegPedidos = itemView.findViewById<TextView>(R.id.tvRegPedidos)
                val tvRegTotal = itemView.findViewById<TextView>(R.id.tvRegTotal)
                val btnVerComprobante = itemView.findViewById<TextView>(R.id.btnVerComprobanteRegistro)
                val btnEliminarRegistro = itemView.findViewById<TextView>(R.id.btnEliminarRegistro)
                val llBannerEliminado = itemView.findViewById<LinearLayout>(R.id.llBannerEliminado)
                val tvInfoEliminacion = itemView.findViewById<TextView>(R.id.tvInfoEliminacion)
                val btnRestaurarRegistro = itemView.findViewById<Button>(R.id.btnRestaurarRegistro)

                btnEliminarRegistro.visibility = View.GONE

                tvRegFechaHora.text = reg.fechaHora

                val textoCliente = if (!reg.celular.isNullOrEmpty() && reg.celular != "S/N") {
                    "👤 Cliente: ${reg.cliente} (📱 Cel: ${reg.celular})"
                } else {
                    "👤 Cliente: ${reg.cliente}"
                }
                tvRegCliente.text = textoCliente

                val icono = when {
                    reg.nombreElemento.contains("Dance", ignoreCase = true) -> "💃"
                    reg.nombreElemento.contains("Car", ignoreCase = true) || reg.nombreElemento.contains("Auto", ignoreCase = true) || reg.nombreElemento.contains("Volante", ignoreCase = true) -> "🏎️"
                    reg.nombreElemento.contains("Egreso", ignoreCase = true) -> "💸"
                    reg.nombreElemento.contains("Mostrador", ignoreCase = true) || reg.tipo.contains("Directa", ignoreCase = true) -> "🛒"
                    else -> "🎬"
                }
                tvRegTitulo.text = "$icono ${reg.nombreElemento}"

                val peliculaText = if (!reg.peliculaJuego.isNullOrBlank()) "Película/Juego: ${reg.peliculaJuego}" else ""
                val tiempoText = if (!reg.tiempoConfigurado.isNullOrBlank()) "Tiempo: ${reg.tiempoConfigurado}" else ""
                val infoExtra = listOf(peliculaText, tiempoText).filter { it.isNotBlank() }.joinToString(" | ")
                tvRegDetalles.text = if (infoExtra.isNotBlank()) infoExtra else "Sin información adicional de tiempo"

                tvRegPedidos.text = "Pedidos: ${if (reg.pedidos.isBlank()) "Ninguno" else reg.pedidos}"

                val montoFormat = if (reg.totalCobrado % 1.0 == 0.0) reg.totalCobrado.toInt().toString() else reg.totalCobrado.toString()
                tvRegTotal.text = "Total: $montoFormat Bs"
                tvRegTotal.setTextColor(if (reg.eliminado) Color.parseColor("#FF5252") else Color.parseColor("#00E676"))

                val metodo = when {
                    reg.metodoPago.startsWith("Mixto", ignoreCase = true) -> "💵📱 ${reg.metodoPago}"
                    reg.metodoPago.equals("QR", ignoreCase = true) -> "📱 QR"
                    else -> "💵 Efectivo"
                }
                tvRegMetodoPago.text = metodo

                if (reg.cantidadVolantes != null && reg.cantidadVolantes!! > 0) {
                    tvRegVolantes.visibility = View.VISIBLE
                    tvRegVolantes.text = "🏎️ Volantes: ${reg.cantidadVolantes}"
                } else {
                    tvRegVolantes.visibility = View.GONE
                }

                if (!reg.comprobanteUri.isNullOrEmpty()) {
                    btnVerComprobante.visibility = View.VISIBLE
                    btnVerComprobante.setOnClickListener {
                        ImageUtils.mostrarDialogoVerComprobante(context, reg.comprobanteUri!!, "Comprobante: ${reg.nombreElemento} (${reg.cliente})")
                    }
                } else {
                    btnVerComprobante.visibility = View.GONE
                }

                if (reg.eliminado) {
                    cardView.setCardBackgroundColor(Color.parseColor("#261418"))
                    llBannerEliminado.visibility = View.VISIBLE
                    val elimPor = reg.eliminadoPor ?: "Personal"
                    val fechaElim = reg.fechaEliminacion ?: "Fecha desconocida"
                    val motivo = reg.motivoEliminacion ?: "Sin motivo registrado"
                    tvInfoEliminacion.text = "⚠️ REGISTRO BORRADO\nPor: $elimPor | Fecha: $fechaElim ($motivo)"
                    btnRestaurarRegistro.visibility = View.VISIBLE
                    btnRestaurarRegistro.setOnClickListener {
                        AlertDialog.Builder(context)
                            .setTitle("Restaurar Registro")
                            .setMessage("¿Deseas restaurar este registro a la base de datos?")
                            .setPositiveButton("Sí, restaurar") { _, _ ->
                                reg.eliminado = false
                                reg.eliminadoPor = null
                                reg.fechaEliminacion = null
                                reg.motivoEliminacion = null
                                database.child("auditoria_registros").child(reg.idFirebase).setValue(reg)
                                database.child("registros").child(reg.idFirebase).setValue(reg)
                                Toast.makeText(context, "Registro restaurado", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }
                } else {
                    cardView.setCardBackgroundColor(Color.parseColor("#1A1C29"))
                    llBannerEliminado.visibility = View.GONE
                }

                llContenedor.addView(itemView)
            }
        }

        etBuscar.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                if (q.isEmpty()) {
                    renderizarRegistros(todosLosRegistros)
                } else {
                    val filtrados = todosLosRegistros.filter {
                        it.cliente.lowercase().contains(q) ||
                                (it.celular ?: "").lowercase().contains(q) ||
                                it.nombreElemento.lowercase().contains(q) ||
                                (it.peliculaJuego ?: "").lowercase().contains(q) ||
                                it.fechaHora.lowercase().contains(q) ||
                                (it.creadoPor ?: "").lowercase().contains(q)
                    }
                    renderizarRegistros(filtrados)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                todosLosRegistros.clear()
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    tvEstado.text = "No hay registros de auditoría disponibles."
                    tvContador.text = "0 registros"
                    return
                }

                for (snap in snapshot.children) {
                    val reg = snap.getValue(RegistroEntry::class.java) ?: continue
                    reg.idFirebase = snap.key ?: ""

                    if (categoriaFiltro != null) {
                        val coincide = when (categoriaFiltro.uppercase()) {
                            "CINE", "MINICINE" -> reg.nombreElemento.contains("Sala", true) || reg.tipo.contains("Cine", true)
                            "AUTITOS", "VOLANTES" -> reg.nombreElemento.contains("Car", true) || reg.nombreElemento.contains("Auto", true) || reg.nombreElemento.contains("Volante", true)
                            "JUST_DANCE", "JUST DANCE" -> reg.nombreElemento.contains("Dance", true)
                            else -> reg.nombreElemento.contains(categoriaFiltro, true) || reg.tipo.contains(categoriaFiltro, true)
                        }
                        if (!coincide) continue
                    }

                    todosLosRegistros.add(reg)
                }

                todosLosRegistros.sortByDescending { it.timestamp }

                val q = etBuscar.text?.toString()?.trim()?.lowercase() ?: ""
                if (q.isEmpty()) {
                    renderizarRegistros(todosLosRegistros)
                } else {
                    val filtrados = todosLosRegistros.filter {
                        it.cliente.lowercase().contains(q) ||
                                (it.celular ?: "").lowercase().contains(q) ||
                                it.nombreElemento.lowercase().contains(q) ||
                                (it.peliculaJuego ?: "").lowercase().contains(q) ||
                                it.fechaHora.lowercase().contains(q) ||
                                (it.creadoPor ?: "").lowercase().contains(q)
                    }
                    renderizarRegistros(filtrados)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                tvEstado.text = "Error al cargar auditoría: ${error.message}"
            }
        }

        database.child("auditoria_registros").limitToLast(250).addValueEventListener(listener)
        dialog.setOnDismissListener {
            database.child("auditoria_registros").removeEventListener(listener)
        }

        dialog.show()
    }
}

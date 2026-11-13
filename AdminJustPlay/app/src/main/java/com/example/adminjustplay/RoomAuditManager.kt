package com.example.adminjustplay

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.res.ColorStateList
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
import java.util.Calendar
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
        mostrarDialogoHistorialAuditoriaPorArea(null, "🛡️ Auditoría de Salas")
    }

    /**
     * Muestra la vista de bitácora de auditoría de salas con filtros gráficos avanzados (fecha, horas, estado, secciones).
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

        // Botones de filtro de estado
        val btnAuditFiltroActivos = dialogView.findViewById<Button>(R.id.btnAuditFiltroActivos)
        val btnAuditFiltroBorrados = dialogView.findViewById<Button>(R.id.btnAuditFiltroBorrados)
        val btnAuditFiltroTodos = dialogView.findViewById<Button>(R.id.btnAuditFiltroTodos)

        // Filtro de fecha y horas
        val btnAuditFiltroFecha = dialogView.findViewById<Button>(R.id.btnAuditFiltroFecha)
        val btnAuditLimpiarFecha = dialogView.findViewById<Button>(R.id.btnAuditLimpiarFecha)
        val btnAuditHoraInicio = dialogView.findViewById<Button>(R.id.btnAuditHoraInicio)
        val btnAuditHoraFin = dialogView.findViewById<Button>(R.id.btnAuditHoraFin)
        val btnAuditLimpiarHoras = dialogView.findViewById<Button>(R.id.btnAuditLimpiarHoras)

        // Checkboxes de sección
        val cbAuditCine = dialogView.findViewById<CheckBox>(R.id.cbAuditCine)
        val cbAuditAutitos = dialogView.findViewById<CheckBox>(R.id.cbAuditAutitos)
        val cbAuditDance = dialogView.findViewById<CheckBox>(R.id.cbAuditDance)
        val llAuditFiltrosSeccionesDinamicas = dialogView.findViewById<LinearLayout>(R.id.llAuditFiltrosSeccionesDinamicas)

        tvTitulo.text = tituloArea
        tvSub.text = "Auditoría forense e inmutable: clientes, celulares, importes cobrados y registros activos/borrados."
        tvResumen.visibility = View.VISIBLE

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCerrar.setOnClickListener { dialog.dismiss() }

        var modoAuditoria = "ACTIVOS" // "ACTIVOS", "BORRADOS", "TODOS"
        var fechaFiltro: String? = null // "dd/MM/yyyy"
        var horaInicioFiltro: String? = null // "HH:mm"
        var horaFinFiltro: String? = null // "HH:mm"

        val dynamicCheckboxes = mutableMapOf<String, CheckBox>()
        val seccionesDinamicas = mutableListOf<SeccionSala>()
        val todosLosRegistros = mutableListOf<RegistroEntry>()

        if (categoriaFiltro != null) {
            val catUpper = categoriaFiltro.uppercase()
            cbAuditCine.isChecked = catUpper in listOf("CINE", "MINICINE")
            cbAuditAutitos.isChecked = catUpper in listOf("AUTITOS", "VOLANTES")
            cbAuditDance.isChecked = catUpper in listOf("JUST_DANCE", "JUST DANCE")
        }

        fun renderizarRegistros(lista: List<RegistroEntry>) {
            llContenedor.removeAllViews()
            tvContador.text = "${lista.size} registros"

            val totalActivos = lista.filter { !it.eliminado }.sumOf { it.totalCobrado }
            val countBorrados = lista.count { it.eliminado }
            val countActivos = lista.count { !it.eliminado }
            val totalActivosFmt = if (totalActivos % 1.0 == 0.0) totalActivos.toInt().toString() else totalActivos.toString()
            tvResumen.text = "📊 ${lista.size} registros (Activos: $countActivos | Borrados: $countBorrados) • Total activo: $totalActivosFmt Bs"

            if (lista.isEmpty()) {
                val tvVacio = TextView(context).apply {
                    text = "No se encontraron registros de auditoría para el criterio seleccionado."
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

        fun aplicarFiltros() {
            val q = etBuscar.text?.toString()?.trim()?.lowercase() ?: ""

            val filtrados = todosLosRegistros.filter { reg ->
                // 1. Filtro Modo Auditoría (Activos / Borrados / Todos)
                when (modoAuditoria) {
                    "ACTIVOS" -> if (reg.eliminado) return@filter false
                    "BORRADOS" -> if (!reg.eliminado) return@filter false
                    "TODOS" -> { }
                }

                // 2. Filtro de Fecha (formato dd/MM/yyyy)
                if (fechaFiltro != null) {
                    if (!reg.fechaHora.startsWith(fechaFiltro!!)) return@filter false
                }

                // 3. Filtro de Rango Horario (formato HH:mm)
                if (horaInicioFiltro != null || horaFinFiltro != null) {
                    val horaRegistro = if (reg.fechaHora.length >= 16) reg.fechaHora.substring(11, 16) else ""
                    if (horaRegistro.isNotEmpty()) {
                        if (horaInicioFiltro != null && horaRegistro < horaInicioFiltro!!) return@filter false
                        if (horaFinFiltro != null && horaRegistro > horaFinFiltro!!) return@filter false
                    }
                }

                // 4. Filtro por Sección (Cines, Volantes, Dance, Secciones Dinámicas)
                val tipo = reg.tipo.lowercase()
                val nombreEl = reg.nombreElemento.lowercase()

                val seccionCoincidente = seccionesDinamicas.find { sec ->
                    val secNom = sec.nombre.lowercase()
                    val secPref = sec.prefijoSala.lowercase()
                    tipo.equals(secNom, ignoreCase = true) ||
                    (secNom.isNotBlank() && (tipo.contains(secNom) || nombreEl.contains(secNom))) ||
                    (secPref.isNotBlank() && nombreEl.contains(secPref))
                }

                val pasaSeccion = when {
                    seccionCoincidente != null -> dynamicCheckboxes[seccionCoincidente.id]?.isChecked ?: true
                    tipo.contains("dance") || nombreEl.contains("dance") -> cbAuditDance.isChecked
                    tipo.contains("auto") || nombreEl.contains("car") || nombreEl.contains("auto") || nombreEl.contains("volante") -> cbAuditAutitos.isChecked
                    tipo.contains("cine") || nombreEl.contains("cine") || (nombreEl.contains("sala") && !tipo.contains("consumo") && !nombreEl.contains("consumo")) -> cbAuditCine.isChecked
                    else -> true
                }
                if (!pasaSeccion) return@filter false

                // 5. Búsqueda por texto libre
                if (q.isNotEmpty()) {
                    val coincideTexto = reg.cliente.lowercase().contains(q) ||
                            (reg.celular ?: "").lowercase().contains(q) ||
                            reg.nombreElemento.lowercase().contains(q) ||
                            (reg.peliculaJuego ?: "").lowercase().contains(q) ||
                            reg.fechaHora.lowercase().contains(q) ||
                            (reg.creadoPor ?: "").lowercase().contains(q) ||
                            (reg.metodoPago ?: "").lowercase().contains(q)
                    if (!coincideTexto) return@filter false
                }

                true
            }

            renderizarRegistros(filtrados)
        }

        fun actualizarEstilosBotones() {
            val colorActivo = Color.parseColor("#DEFF9A")
            val colorBorrados = Color.parseColor("#FF5252")
            val colorFondo = Color.parseColor("#24283B")

            when (modoAuditoria) {
                "ACTIVOS" -> {
                    btnAuditFiltroActivos.backgroundTintList = ColorStateList.valueOf(colorActivo)
                    btnAuditFiltroActivos.setTextColor(Color.BLACK)
                    btnAuditFiltroBorrados.backgroundTintList = ColorStateList.valueOf(colorFondo)
                    btnAuditFiltroBorrados.setTextColor(colorBorrados)
                    btnAuditFiltroTodos.backgroundTintList = ColorStateList.valueOf(colorFondo)
                    btnAuditFiltroTodos.setTextColor(Color.WHITE)
                }
                "BORRADOS" -> {
                    btnAuditFiltroActivos.backgroundTintList = ColorStateList.valueOf(colorFondo)
                    btnAuditFiltroActivos.setTextColor(Color.WHITE)
                    btnAuditFiltroBorrados.backgroundTintList = ColorStateList.valueOf(colorBorrados)
                    btnAuditFiltroBorrados.setTextColor(Color.WHITE)
                    btnAuditFiltroTodos.backgroundTintList = ColorStateList.valueOf(colorFondo)
                    btnAuditFiltroTodos.setTextColor(Color.WHITE)
                }
                "TODOS" -> {
                    btnAuditFiltroActivos.backgroundTintList = ColorStateList.valueOf(colorFondo)
                    btnAuditFiltroActivos.setTextColor(Color.WHITE)
                    btnAuditFiltroBorrados.backgroundTintList = ColorStateList.valueOf(colorFondo)
                    btnAuditFiltroBorrados.setTextColor(colorBorrados)
                    btnAuditFiltroTodos.backgroundTintList = ColorStateList.valueOf(colorActivo)
                    btnAuditFiltroTodos.setTextColor(Color.BLACK)
                }
            }
        }

        btnAuditFiltroActivos.setOnClickListener {
            modoAuditoria = "ACTIVOS"
            actualizarEstilosBotones()
            aplicarFiltros()
        }
        btnAuditFiltroBorrados.setOnClickListener {
            modoAuditoria = "BORRADOS"
            actualizarEstilosBotones()
            aplicarFiltros()
        }
        btnAuditFiltroTodos.setOnClickListener {
            modoAuditoria = "TODOS"
            actualizarEstilosBotones()
            aplicarFiltros()
        }
        actualizarEstilosBotones()

        btnAuditFiltroFecha.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(context, { _, year, month, day ->
                fechaFiltro = String.format("%02d/%02d/%d", day, month + 1, year)
                btnAuditFiltroFecha.text = "📅 $fechaFiltro"
                btnAuditFiltroFecha.setTextColor(Color.parseColor("#DEFF9A"))
                aplicarFiltros()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnAuditLimpiarFecha.setOnClickListener {
            fechaFiltro = null
            btnAuditFiltroFecha.text = "📅 Fecha: Todas"
            btnAuditFiltroFecha.setTextColor(Color.parseColor("#A0A0A0"))
            aplicarFiltros()
        }

        btnAuditHoraInicio.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(context, { _, hourOfDay, minute ->
                horaInicioFiltro = String.format("%02d:%02d", hourOfDay, minute)
                btnAuditHoraInicio.text = "⏰ Desde: $horaInicioFiltro"
                btnAuditHoraInicio.setTextColor(Color.parseColor("#DEFF9A"))
                aplicarFiltros()
            }, cal.get(Calendar.HOUR_OF_DAY), 0, true).show()
        }

        btnAuditHoraFin.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(context, { _, hourOfDay, minute ->
                horaFinFiltro = String.format("%02d:%02d", hourOfDay, minute)
                btnAuditHoraFin.text = "⏰ Hasta: $horaFinFiltro"
                btnAuditHoraFin.setTextColor(Color.parseColor("#DEFF9A"))
                aplicarFiltros()
            }, cal.get(Calendar.HOUR_OF_DAY), 59, true).show()
        }

        btnAuditLimpiarHoras.setOnClickListener {
            horaInicioFiltro = null
            horaFinFiltro = null
            btnAuditHoraInicio.text = "⏰ Desde: Inicio"
            btnAuditHoraInicio.setTextColor(Color.WHITE)
            btnAuditHoraFin.text = "⏰ Hasta: Fin"
            btnAuditHoraFin.setTextColor(Color.WHITE)
            aplicarFiltros()
        }

        cbAuditCine.setOnCheckedChangeListener { _, _ -> aplicarFiltros() }
        cbAuditAutitos.setOnCheckedChangeListener { _, _ -> aplicarFiltros() }
        cbAuditDance.setOnCheckedChangeListener { _, _ -> aplicarFiltros() }

        // Cargar secciones dinámicas para crear checkboxes
        database.child("secciones_salas").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                seccionesDinamicas.clear()
                llAuditFiltrosSeccionesDinamicas.removeAllViews()
                dynamicCheckboxes.clear()

                for (snap in snapshot.children) {
                    val sec = snap.getValue(SeccionSala::class.java) ?: continue
                    sec.id = snap.key ?: sec.id
                    if (sec.id.lowercase() in listOf("minicine", "autitos", "just_dance")) continue
                    seccionesDinamicas.add(sec)

                    val cb = CheckBox(context).apply {
                        text = "${sec.icono} ${sec.nombre}"
                        setTextColor(Color.WHITE)
                        textSize = 12f
                        isChecked = (categoriaFiltro == null || categoriaFiltro.equals(sec.nombre, ignoreCase = true) || categoriaFiltro.equals(sec.id, ignoreCase = true))
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.marginStart = 16
                        layoutParams = lp
                        setOnCheckedChangeListener { _, _ -> aplicarFiltros() }
                    }
                    dynamicCheckboxes[sec.id] = cb
                    llAuditFiltrosSeccionesDinamicas.addView(cb)
                }
                aplicarFiltros()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        etBuscar.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                aplicarFiltros()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                todosLosRegistros.clear()
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    tvEstado.text = "No hay registros de auditoría disponibles."
                    tvContador.text = "0 registros"
                    renderizarRegistros(emptyList())
                    return
                }

                for (snap in snapshot.children) {
                    val reg = snap.getValue(RegistroEntry::class.java) ?: continue
                    reg.idFirebase = snap.key ?: ""
                    todosLosRegistros.add(reg)
                }

                todosLosRegistros.sortByDescending { it.timestamp }
                tvEstado.visibility = View.GONE
                aplicarFiltros()
            }

            override fun onCancelled(error: DatabaseError) {
                tvEstado.text = "Error al cargar auditoría: ${error.message}"
            }
        }

        database.child("auditoria_registros").limitToLast(500).addValueEventListener(listener)
        dialog.setOnDismissListener {
            database.child("auditoria_registros").removeEventListener(listener)
        }

        dialog.show()
    }
}

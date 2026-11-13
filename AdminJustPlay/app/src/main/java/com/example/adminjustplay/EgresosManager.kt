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
 * Modelo de datos para Egresos y Salidas de Caja.
 */
data class Egreso(
    val id: String = "",
    val categoria: String = "", // "Gastos Operativos / Insumos", "Nómina / Salarios"
    val descripcion: String = "",
    val monto: Double = 0.0,
    val metodoPago: String = "Efectivo", // "Efectivo", "QR"
    val usuarioNombre: String = "",
    val usuarioRol: String = "",
    val dispositivoModelo: String = "",
    val fechaHora: String = "",
    val timestamp: Long = 0L
)

/**
 * Administra el registro y consulta de egresos de caja con control de roles.
 */
class EgresosManager(
    private val context: Context,
    private val database: DatabaseReference
) {

    fun mostrarDialogoGestionEgresos(
        esAdmin: Boolean = true,
        usuarioNombre: String = "Admin",
        usuarioRol: String = "Administrador"
    ) {
        val opciones = arrayOf(
            "➕ Registrar Nuevo Egreso / Gasto",
            "📊 Ver Historial y Control de Egresos"
        )
        AlertDialog.Builder(context)
            .setTitle("💸 Control de Egresos")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> mostrarDialogoNuevoEgreso(esAdmin, usuarioNombre, usuarioRol)
                    1 -> mostrarDialogoHistorialEgresos(esAdmin)
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    fun registrarEgreso(
        categoria: String,
        descripcion: String,
        monto: Double,
        metodoPago: String,
        usuarioNombre: String,
        usuarioRol: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val timestamp = System.currentTimeMillis()
        val dispositivoModelo = DeviceInfoProvider.getDeviceModel()

        val egresoId = database.child("egresos").push().key ?: return
        val egresoData = mapOf(
            "id" to egresoId,
            "categoria" to categoria,
            "descripcion" to descripcion,
            "monto" to monto,
            "metodoPago" to metodoPago,
            "usuarioNombre" to usuarioNombre,
            "usuarioRol" to usuarioRol,
            "dispositivoModelo" to dispositivoModelo,
            "fechaHora" to now,
            "timestamp" to timestamp
        )

        database.child("egresos").child(egresoId).setValue(egresoData)
            .addOnSuccessListener {
                // Sincronizar también a registros para que aparezca en el historial unificado y auditoría
                val regEntry = RegistroEntry(
                    idFirebase = egresoId,
                    tipo = "Egreso",
                    nombreElemento = "Egreso: $categoria",
                    cliente = descripcion,
                    fechaHora = now,
                    timestamp = timestamp,
                    totalCobrado = monto,
                    metodoPago = metodoPago,
                    creadoPor = "$usuarioNombre ($usuarioRol)",
                    eliminado = false
                )
                database.child("registros").child(egresoId).setValue(regEntry)
                database.child("auditoria_registros").child(egresoId).setValue(regEntry)
                onComplete(true)
            }
            .addOnFailureListener { onComplete(false) }
    }

    fun mostrarDialogoNuevoEgreso(
        esAdmin: Boolean,
        usuarioNombre: String,
        usuarioRol: String,
        onEgresoRegistrado: () -> Unit = {}
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_nuevo_egreso, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnCerrar = dialogView.findViewById<TextView>(R.id.btnCerrarNuevoEgreso)
        val etMonto = dialogView.findViewById<EditText>(R.id.etMontoEgreso)
        val spCategoria = dialogView.findViewById<Spinner>(R.id.spCategoriaEgreso)
        val etDescripcion = dialogView.findViewById<EditText>(R.id.etDescripcionEgreso)
        val rbEfectivo = dialogView.findViewById<RadioButton>(R.id.rbEgresoEfectivo)
        val btnConfirmar = dialogView.findViewById<Button>(R.id.btnConfirmarEgreso)

        val btnP10 = dialogView.findViewById<Button>(R.id.btnPresetEgreso10)
        val btnP20 = dialogView.findViewById<Button>(R.id.btnPresetEgreso20)
        val btnP50 = dialogView.findViewById<Button>(R.id.btnPresetEgreso50)
        val btnP100 = dialogView.findViewById<Button>(R.id.btnPresetEgreso100)

        fun setMontoPreset(valor: Double) {
            val actual = etMonto.text.toString().toDoubleOrNull() ?: 0.0
            val nuevo = if (actual == 0.0) valor else actual + valor
            etMonto.setText(if (nuevo % 1.0 == 0.0) nuevo.toInt().toString() else nuevo.toString())
            etMonto.setSelection(etMonto.text.length)
        }

        btnP10?.setOnClickListener { setMontoPreset(10.0) }
        btnP20?.setOnClickListener { setMontoPreset(20.0) }
        btnP50?.setOnClickListener { setMontoPreset(50.0) }
        btnP100?.setOnClickListener { setMontoPreset(100.0) }

        val categorias = if (esAdmin) {
            listOf("Gastos Operativos / Insumos", "Nómina / Salarios / Adelantos", "Mantenimiento / Reparación", "Compras / Reposición")
        } else {
            listOf("Gastos Operativos / Insumos", "Mantenimiento / Reparación")
        }
        val adapterCat = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, categorias) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val tv = super.getView(position, convertView, parent) as TextView
                tv.setTextColor(Color.WHITE)
                tv.textSize = 13f
                return tv
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val tv = super.getDropDownView(position, convertView, parent) as TextView
                tv.setBackgroundColor(Color.parseColor("#24283B"))
                tv.setTextColor(Color.WHITE)
                tv.setPadding(24, 20, 24, 20)
                return tv
            }
        }
        spCategoria.adapter = adapterCat

        btnCerrar?.setOnClickListener { dialog.dismiss() }

        btnConfirmar.setOnClickListener {
            val monto = etMonto.text.toString().trim().toDoubleOrNull()
            val desc = etDescripcion.text.toString().trim()
            val cat = spCategoria.selectedItem?.toString() ?: "Gastos Operativos / Insumos"
            val metodo = if (rbEfectivo.isChecked) "Efectivo" else "QR"

            if (monto == null || monto <= 0) {
                Toast.makeText(context, "Ingresa un monto válido para el egreso.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (desc.isEmpty()) {
                Toast.makeText(context, "Ingresa el motivo o detalle del gasto.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registrarEgreso(cat, desc, monto, metodo, usuarioNombre, usuarioRol) { ok ->
                if (ok) {
                    Toast.makeText(context, "✅ Egreso de $monto Bs registrado exitosamente.", Toast.LENGTH_LONG).show()
                    onEgresoRegistrado()
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "Error al registrar egreso.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    fun mostrarDialogoHistorialEgresos(esAdmin: Boolean) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_historial_auditoria, null)
        val tvTitulo = dialogView.findViewById<TextView>(R.id.tvDialogAuditTitulo)
        val tvContador = dialogView.findViewById<TextView>(R.id.tvDialogAuditContador)
        val tvSub = dialogView.findViewById<TextView>(R.id.tvDialogAuditSubtitulo)
        val tvResumen = dialogView.findViewById<TextView>(R.id.tvDialogAuditResumen)
        val btnCerrar = dialogView.findViewById<TextView>(R.id.btnDialogAuditCerrar)
        val etBuscar = dialogView.findViewById<EditText>(R.id.etDialogAuditBuscar)
        val llContenedor = dialogView.findViewById<LinearLayout>(R.id.llDialogAuditContenedor)
        val tvEstado = dialogView.findViewById<TextView>(R.id.tvDialogAuditEstado)

        tvTitulo.text = "💸 Salidas de Caja & Egresos"
        tvSub.text = "Historial contable y trazabilidad de gastos operativos, insumos y nómina."
        tvResumen.visibility = View.VISIBLE

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCerrar.setOnClickListener { dialog.dismiss() }

        val todosLosItems = mutableListOf<Egreso>()

        fun renderizarEgresos(lista: List<Egreso>) {
            llContenedor.removeAllViews()
            tvContador.text = "${lista.size} registros"

            val total = lista.sumOf { it.monto }
            val totalEfec = lista.filter { it.metodoPago.equals("Efectivo", true) }.sumOf { it.monto }
            val totalQR = lista.filter { it.metodoPago.equals("QR", true) }.sumOf { it.monto }
            tvResumen.text = "💰 Total Egresos: -$total Bs (💵 Efectivo: -$totalEfec Bs | 📱 QR: -$totalQR Bs)"

            if (lista.isEmpty()) {
                val tvVacio = TextView(context).apply {
                    text = "No se encontraron egresos para el criterio de búsqueda."
                    setTextColor(Color.parseColor("#888888"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, 24, 0, 24)
                }
                llContenedor.addView(tvVacio)
                return
            }

            for (eg in lista) {
                val itemView = LayoutInflater.from(context).inflate(R.layout.item_egreso_card, llContenedor, false)

                val tvFecha = itemView.findViewById<TextView>(R.id.tvEgresoFechaHora)
                val tvBadge = itemView.findViewById<TextView>(R.id.tvEgresoBadgeMetodo)
                val tvCat = itemView.findViewById<TextView>(R.id.tvEgresoCategoria)
                val tvDesc = itemView.findViewById<TextView>(R.id.tvEgresoDescripcion)
                val tvResp = itemView.findViewById<TextView>(R.id.tvEgresoResponsable)
                val tvMonto = itemView.findViewById<TextView>(R.id.tvEgresoMonto)

                tvFecha.text = eg.fechaHora

                val isQR = eg.metodoPago.equals("QR", true)
                tvBadge.text = if (isQR) "📱 QR" else "💵 Efectivo"
                tvBadge.setTextColor(if (isQR) Color.parseColor("#00E5FF") else Color.parseColor("#DEFF9A"))
                tvBadge.setBackgroundColor(if (isQR) Color.parseColor("#0D3349") else Color.parseColor("#1B3E2B"))

                tvCat.text = "💸 ${eg.categoria}"
                tvDesc.text = eg.descripcion

                val dispStr = if (eg.dispositivoModelo.isNotBlank()) eg.dispositivoModelo else "Terminal POS / Móvil"
                tvResp.text = "👤 ${eg.usuarioNombre} (${eg.usuarioRol}) • 📱 $dispStr"

                val montoFormat = if (eg.monto % 1.0 == 0.0) eg.monto.toInt().toString() else eg.monto.toString()
                tvMonto.text = "-$montoFormat Bs"

                llContenedor.addView(itemView)
            }
        }

        etBuscar.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                if (q.isEmpty()) {
                    renderizarEgresos(todosLosItems)
                } else {
                    val filtrados = todosLosItems.filter {
                        it.categoria.lowercase().contains(q) ||
                                it.descripcion.lowercase().contains(q) ||
                                it.usuarioNombre.lowercase().contains(q) ||
                                it.metodoPago.lowercase().contains(q) ||
                                it.fechaHora.lowercase().contains(q) ||
                                it.monto.toString().contains(q)
                    }
                    renderizarEgresos(filtrados)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                todosLosItems.clear()
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    tvEstado.text = "No se han registrado egresos."
                    tvContador.text = "0 registros"
                    tvResumen.text = "Total Egresos: 0.0 Bs"
                    return
                }

                for (snap in snapshot.children) {
                    val cat = snap.child("categoria").getValue(String::class.java) ?: "Gastos Operativos / Insumos"
                    if (!esAdmin && cat.contains("Nómina", ignoreCase = true)) {
                        continue
                    }

                    val eg = Egreso(
                        id = snap.child("id").getValue(String::class.java) ?: snap.key ?: "",
                        categoria = cat,
                        descripcion = snap.child("descripcion").getValue(String::class.java) ?: "",
                        monto = snap.child("monto").getValue(Double::class.java) ?: 0.0,
                        metodoPago = snap.child("metodoPago").getValue(String::class.java) ?: "Efectivo",
                        usuarioNombre = snap.child("usuarioNombre").getValue(String::class.java) ?: "Usuario",
                        usuarioRol = snap.child("usuarioRol").getValue(String::class.java) ?: "Empleado",
                        dispositivoModelo = snap.child("dispositivoModelo").getValue(String::class.java) ?: "",
                        fechaHora = snap.child("fechaHora").getValue(String::class.java) ?: "",
                        timestamp = snap.child("timestamp").getValue(Long::class.java) ?: 0L
                    )
                    todosLosItems.add(eg)
                }

                todosLosItems.sortByDescending { it.timestamp }

                val q = etBuscar.text?.toString()?.trim()?.lowercase() ?: ""
                if (q.isEmpty()) {
                    renderizarEgresos(todosLosItems)
                } else {
                    val filtrados = todosLosItems.filter {
                        it.categoria.lowercase().contains(q) ||
                                it.descripcion.lowercase().contains(q) ||
                                it.usuarioNombre.lowercase().contains(q) ||
                                it.metodoPago.lowercase().contains(q) ||
                                it.fechaHora.lowercase().contains(q) ||
                                it.monto.toString().contains(q)
                    }
                    renderizarEgresos(filtrados)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                tvEstado.text = "Error al cargar egresos: ${error.message}"
            }
        }

        database.child("egresos").limitToLast(100).addValueEventListener(listener)
        dialog.setOnDismissListener {
            database.child("egresos").removeEventListener(listener)
        }

        dialog.show()
    }
}

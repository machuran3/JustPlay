package com.example.minicine01

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
 * Administra el registro y consulta de egresos de caja para empleados (Gastos Operativos / Insumos).
 */
class EgresosManager(
    private val context: Context,
    private val database: DatabaseReference
) {

    fun mostrarDialogoGestionEgresos(
        esAdmin: Boolean = false,
        usuarioNombre: String = "Empleado",
        usuarioRol: String = "Empleado"
    ) {
        val opciones = arrayOf(
            "➕ Registrar Salida de Dinero / Insumos",
            "📊 Ver Historial de Egresos Operativos"
        )
        AlertDialog.Builder(context)
            .setTitle("💸 Salidas de Caja / Egresos")
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
        esAdmin: Boolean = false,
        usuarioNombre: String = "Empleado",
        usuarioRol: String = "Empleado",
        onEgresoRegistrado: () -> Unit = {}
    ) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.parseColor("#1A1C29"))
        }

        val tvTitulo = TextView(context).apply {
            text = "💸 Registrar Salida de Caja / Egreso"
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        layout.addView(tvTitulo)

        val tvCat = TextView(context).apply {
            text = "Categoría del Gasto:"
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 12f
        }
        layout.addView(tvCat)

        val spinnerCat = Spinner(context).apply {
            val categorias = if (esAdmin) {
                listOf("Gastos Operativos / Insumos", "Nómina / Salarios")
            } else {
                listOf("Gastos Operativos / Insumos")
            }
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, categorias)
        }
        layout.addView(spinnerCat)

        val etMonto = EditText(context).apply {
            hint = "Monto (Bs)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }
        layout.addView(etMonto)

        val etDesc = EditText(context).apply {
            hint = "Descripción (Ej: Servilletas, focos, detergente)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
        }
        layout.addView(etDesc)

        val rgMetodo = RadioGroup(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }
        val rbEfec = RadioButton(context).apply {
            text = "💵 Efectivo (Caja)"
            setTextColor(Color.WHITE)
            isChecked = true
        }
        val rbQR = RadioButton(context).apply {
            text = "📱 QR"
            setTextColor(Color.WHITE)
        }
        rgMetodo.addView(rbEfec)
        rgMetodo.addView(rbQR)
        layout.addView(rgMetodo)

        val dialog = AlertDialog.Builder(context)
            .setView(layout)
            .setPositiveButton("Guardar Egreso", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val monto = etMonto.text.toString().trim().toDoubleOrNull()
            val desc = etDesc.text.toString().trim()
            val cat = spinnerCat.selectedItem?.toString() ?: "Gastos Operativos / Insumos"
            val metodo = if (rbEfec.isChecked) "Efectivo" else "QR"

            if (monto == null || monto <= 0) {
                Toast.makeText(context, "Ingresa un monto válido.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (desc.isEmpty()) {
                Toast.makeText(context, "Ingresa el motivo del egreso.", Toast.LENGTH_SHORT).show()
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
    }

    fun mostrarDialogoHistorialEgresos(esAdmin: Boolean = false) {
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
            text = "💸 Historial de Egresos Operativos"
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
            text = "Registro contable de gastos de insumos y servicios menores."
            setTextColor(Color.parseColor("#A0A0A0"))
            textSize = 12f
            setPadding(0, 6, 0, 16)
        }
        container.addView(tvSub)

        val tvResumenTotal = TextView(context).apply {
            text = "Total Egresos: Calculando..."
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        container.addView(tvResumenTotal)

        val llLista = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(llLista)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                llLista.removeAllViews()
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    val tvVacio = TextView(context).apply {
                        text = "No se han registrado egresos."
                        setTextColor(Color.parseColor("#888888"))
                        textSize = 13f
                    }
                    llLista.addView(tvVacio)
                    tvResumenTotal.text = "Total Egresos: 0.0 Bs"
                    return
                }

                val items = mutableListOf<Egreso>()
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
                    items.add(eg)
                }

                items.sortByDescending { it.timestamp }

                val total = items.sumOf { it.monto }
                val totalEfec = items.filter { it.metodoPago.equals("Efectivo", true) }.sumOf { it.monto }
                val totalQR = items.filter { it.metodoPago.equals("QR", true) }.sumOf { it.monto }
                tvResumenTotal.text = "Total Egresos: ${total} Bs (Efec: ${totalEfec} Bs | QR: ${totalQR} Bs)"

                for (eg in items) {
                    val card = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16, 12, 16, 12)
                        setBackgroundColor(Color.parseColor("#24283B"))
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.setMargins(0, 0, 0, 8)
                        layoutParams = lp
                    }

                    val tvCatMonto = TextView(context).apply {
                        text = "🔻 [${eg.categoria}] - ${eg.monto} Bs (${eg.metodoPago})"
                        setTextColor(Color.parseColor("#FF5252"))
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                    }

                    val tvDesc = TextView(context).apply {
                        text = "📝 ${eg.descripcion}"
                        setTextColor(Color.WHITE)
                        textSize = 12f
                    }

                    val tvUser = TextView(context).apply {
                        text = "👤 ${eg.usuarioNombre} (${eg.usuarioRol}) | 📱 ${eg.dispositivoModelo}"
                        setTextColor(Color.parseColor("#DEFF9A"))
                        textSize = 11f
                    }

                    val tvFecha = TextView(context).apply {
                        text = "🕒 ${eg.fechaHora}"
                        setTextColor(Color.parseColor("#A0A0A0"))
                        textSize = 11f
                    }

                    card.addView(tvCatMonto)
                    card.addView(tvDesc)
                    card.addView(tvUser)
                    card.addView(tvFecha)
                    llLista.addView(card)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        database.child("egresos").limitToLast(100).addValueEventListener(listener)
        dialog.setOnDismissListener {
            database.child("egresos").removeEventListener(listener)
        }

        dialog.show()
    }
}

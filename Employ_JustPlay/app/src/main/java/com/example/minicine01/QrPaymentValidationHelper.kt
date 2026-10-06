package com.example.minicine01

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.widget.*
import com.google.firebase.database.*

/**
 * Escucha solicitudes de cobro QR emitidas desde la PC y permite a los empleados
 * validarlas y aprobarlas adjuntando comprobante en tiempo real.
 */
class QrPaymentValidationHelper(
    private val activity: Activity,
    private val database: DatabaseReference,
    private val onTomarFotoCallback: (solicitudId: String) -> Unit
) {

    private val solicitudesMostradas = mutableSetOf<String>()
    private val dialogosActivos = mutableMapOf<String, AlertDialog>()
    private var listener: ChildEventListener? = null

    fun iniciarEscucha() {
        if (listener != null) return

        val ref = database.child("solicitudes_pago_qr")
        listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                procesarSolicitud(snapshot)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val id = snapshot.key ?: return
                val estado = snapshot.child("estado").getValue(String::class.java) ?: ""
                if (estado != "PENDIENTE") {
                    // Si otro dispositivo tomó o finalizó la solicitud, cerrar el diálogo en este celular
                    activity.runOnUiThread {
                        dialogosActivos.remove(id)?.let { dialog ->
                            if (dialog.isShowing) {
                                dialog.dismiss()
                            }
                        }
                    }
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val id = snapshot.key ?: return
                activity.runOnUiThread {
                    dialogosActivos.remove(id)?.let { dialog ->
                        if (dialog.isShowing) dialog.dismiss()
                    }
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        ref.limitToLast(20).addChildEventListener(listener!!)
    }

    fun detenerEscucha() {
        listener?.let {
            database.child("solicitudes_pago_qr").removeEventListener(it)
            listener = null
        }
        dialogosActivos.values.forEach { if (it.isShowing) it.dismiss() }
        dialogosActivos.clear()
    }

    private fun procesarSolicitud(snapshot: DataSnapshot) {
        val id = snapshot.key ?: return
        val estado = snapshot.child("estado").getValue(String::class.java) ?: "PENDIENTE"
        val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

        val ahora = System.currentTimeMillis()
        if (estado == "PENDIENTE" && (ahora - timestamp < 15 * 60 * 1000) && !solicitudesMostradas.contains(id)) {
            solicitudesMostradas.add(id)

            val tipo = snapshot.child("tipo").getValue(String::class.java) ?: "VENTA_MOSTRADOR"
            val salaNombre = snapshot.child("salaNombre").getValue(String::class.java) ?: "Mostrador"
            val cliente = snapshot.child("cliente").getValue(String::class.java) ?: "Cliente"
            val montoTotal = snapshot.child("montoTotal").getValue(Double::class.java) ?: 0.0
            val montoQR = snapshot.child("montoQR").getValue(Double::class.java) ?: montoTotal
            val montoEfectivo = snapshot.child("montoEfectivo").getValue(Double::class.java) ?: 0.0
            val metodoPago = snapshot.child("metodoPago").getValue(String::class.java) ?: "QR"

            mostrarDialogoValidacionQR(
                solicitudId = id,
                tipo = tipo,
                salaNombre = salaNombre,
                cliente = cliente,
                montoTotal = montoTotal,
                montoQR = montoQR,
                montoEfectivo = montoEfectivo,
                metodoPago = metodoPago
            )
        }
    }

    fun mostrarDialogoValidacionQR(
        solicitudId: String,
        tipo: String,
        salaNombre: String,
        cliente: String,
        montoTotal: Double,
        montoQR: Double,
        montoEfectivo: Double,
        metodoPago: String
    ) {
        activity.runOnUiThread {
            val layout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 24, 28, 24)
                setBackgroundColor(Color.parseColor("#1A1C29"))
            }

            val tvTitulo = TextView(activity).apply {
                text = "🔔 Solicitud de Validación y Comprobante"
                setTextColor(Color.parseColor("#DEFF9A"))
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 10)
            }
            layout.addView(tvTitulo)

            val tvDetalle = TextView(activity).apply {
                text = "📍 Destino: $salaNombre ($tipo)\n👤 Cliente: $cliente\n💳 Método: $metodoPago\n📱 Monto en QR: $montoQR Bs ${if (montoEfectivo > 0) "| 💵 Efec: $montoEfectivo Bs" else ""}\n💰 Total Venta: $montoTotal Bs"
                setTextColor(Color.WHITE)
                textSize = 13f
                setPadding(0, 0, 0, 14)
            }
            layout.addView(tvDetalle)

            val tvInstruccion = TextView(activity).apply {
                text = "¿Deseas tomar o adjuntar la foto del comprobante para esta venta solicitada desde la PC?"
                setTextColor(Color.parseColor("#A0A0A0"))
                textSize = 12f
                setPadding(0, 0, 0, 14)
            }
            layout.addView(tvInstruccion)

            val dialog = AlertDialog.Builder(activity)
                .setView(layout)
                .setPositiveButton("📸 Aceptar (Tomar Foto)", null)
                .setNegativeButton("❌ Rechazar", null)
                .setCancelable(false)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialogosActivos[solicitudId] = dialog
            dialog.show()

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                // Reclamar la solicitud para que desaparezca en los demás celulares
                database.child("solicitudes_pago_qr").child(solicitudId).updateChildren(
                    mapOf(
                        "estado" to "EN_PROCESO",
                        "tomadoPor" to DeviceInfoProvider.getDeviceModel()
                    )
                )
                dialogosActivos.remove(solicitudId)
                dialog.dismiss()
                // Abrir directamente la selección de cámara o galería
                onTomarFotoCallback(solicitudId)
            }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                // Si rechaza/cancela, simplemente se ignora y no ocurre nada
                dialogosActivos.remove(solicitudId)
                dialog.dismiss()
            }
        }
    }

    fun aprobarSolicitud(solicitudId: String, comprobanteUrl: String?) {
        val updates = mutableMapOf<String, Any>(
            "estado" to "APROBADO",
            "atendidoPor" to "Empleado",
            "dispositivoAtendio" to DeviceInfoProvider.getDeviceModel(),
            "timestampAprobado" to System.currentTimeMillis()
        )
        if (!comprobanteUrl.isNullOrEmpty()) {
            updates["comprobanteUrl"] = comprobanteUrl
        }
        database.child("solicitudes_pago_qr").child(solicitudId).updateChildren(updates)
        Toast.makeText(activity, "✅ Comprobante enviado a la PC con éxito.", Toast.LENGTH_SHORT).show()
    }

    fun rechazarSolicitud(solicitudId: String) {
        val updates = mapOf<String, Any>(
            "estado" to "RECHAZADO",
            "atendidoPor" to "Empleado",
            "dispositivoAtendio" to DeviceInfoProvider.getDeviceModel()
        )
        database.child("solicitudes_pago_qr").child(solicitudId).updateChildren(updates)
        Toast.makeText(activity, "❌ Pago QR rechazado.", Toast.LENGTH_SHORT).show()
    }
}

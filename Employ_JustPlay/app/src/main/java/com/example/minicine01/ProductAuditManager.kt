package com.example.minicine01

import android.content.Context
import com.google.firebase.database.DatabaseReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single Responsibility Principle (SRP):
 * Registra operaciones de auditoría sobre el catálogo de productos ejecutadas por el Empleado.
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
        usuarioNombre: String = "Empleado",
        usuarioRol: String = "Empleado",
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
}

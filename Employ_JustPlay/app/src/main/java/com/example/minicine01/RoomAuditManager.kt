package com.example.minicine01

import android.content.Context
import com.google.firebase.database.DatabaseReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single Responsibility Principle (SRP):
 * Registra eventos de auditoría de salas y dispositivos ejecutados por el Empleado.
 */
class RoomAuditManager(
    private val context: Context,
    private val database: DatabaseReference
) {

    fun registrarAccionSala(
        tipoAccion: String,
        salaId: String,
        salaNombre: String,
        categoria: String,
        tiempoMinutos: Int = 0,
        usuarioNombre: String = "Empleado",
        usuarioRol: String = "Empleado",
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
}

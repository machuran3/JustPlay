package com.example.minicine01

import android.content.Context
import android.os.Build
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object PresenciaManager {

    private const val PREFS_NAME = "employ_justplay_user_prefs"
    private const val KEY_USER_ID = "usuario_id"
    private const val KEY_USER_NAME = "usuario_nombre"
    private const val KEY_USER_ROLE = "usuario_rol"
    private const val KEY_USER_PIN = "usuario_pin"
    private const val KEY_TIEMPO_LIMITE = "usuario_tiempo_limite"
    private const val KEY_TIPO_TIEMPO = "usuario_tipo_tiempo"
    private const val KEY_FECHA_FIN = "usuario_fecha_fin"
    private const val KEY_BLOQUEADO = "usuario_bloqueado"
    private const val KEY_MOTIVO_BLOQUEO = "usuario_motivo_bloqueo"
    private const val KEY_SESION_AUTENTICADA = "sesion_autenticada"

    private var databaseRef: DatabaseReference? = null
    private var miUsuarioId: String = ""
    private var miNombre: String = ""
    private var miRol: String = "Empleado"
    private var miApp: String = "Employ_JustPlay"
    private var miPin: String = "0001"
    private var miTiempoLimiteMillis: Long = 0L
    private var miTipoTiempoAcceso: String = "Indefinido"
    private var miFechaFinAccesoTexto: String = "Sin límite"
    private var estaBloqueadoAdmin: Boolean = false
    private var motivoBloqueoAdmin: String = ""

    var onEstadoCambioListener: ((UsuarioPresencia) -> Unit)? = null

    fun guardarSesionAutenticada(context: Context, autenticada: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SESION_AUTENTICADA, autenticada).apply()
    }

    fun estaSesionAutenticada(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SESION_AUTENTICADA, false)
    }

    fun obtenerOCrearUsuarioId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_USER_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString().substring(0, 8)
            prefs.edit().putString(KEY_USER_ID, id).apply()
        }
        miUsuarioId = id
        return id
    }

    fun obtenerNombreUsuario(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultName = "Empleado - ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        return prefs.getString(KEY_USER_NAME, defaultName) ?: defaultName
    }

    fun obtenerPinUsuario(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_PIN, "0001") ?: "0001"
    }

    fun obtenerUsuarioActualModel(): UsuarioPresencia {
        return UsuarioPresencia(
            id = miUsuarioId,
            nombre = miNombre,
            rol = miRol,
            app = miApp,
            dispositivo = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            enLinea = true,
            bloqueado = estaBloqueadoAdmin,
            motivoBloqueo = motivoBloqueoAdmin,
            pin = miPin,
            tiempoLimiteMillis = miTiempoLimiteMillis,
            tipoTiempoAcceso = miTipoTiempoAcceso,
            fechaFinAccesoTexto = miFechaFinAccesoTexto
        )
    }

    fun iniciarMonitoreoPresencia(context: Context, appName: String = "Employ_JustPlay", defaultRole: String = "Empleado") {
        obtenerOCrearUsuarioId(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        
        miNombre = prefs.getString(KEY_USER_NAME, "Empleado - $deviceModel") ?: "Empleado - $deviceModel"
        miRol = prefs.getString(KEY_USER_ROLE, defaultRole) ?: defaultRole
        miPin = prefs.getString(KEY_USER_PIN, "0001") ?: "0001"
        miTiempoLimiteMillis = prefs.getLong(KEY_TIEMPO_LIMITE, 0L)
        miTipoTiempoAcceso = prefs.getString(KEY_TIPO_TIEMPO, "Indefinido") ?: "Indefinido"
        miFechaFinAccesoTexto = prefs.getString(KEY_FECHA_FIN, "Sin límite") ?: "Sin límite"
        estaBloqueadoAdmin = prefs.getBoolean(KEY_BLOQUEADO, false)
        motivoBloqueoAdmin = prefs.getString(KEY_MOTIVO_BLOQUEO, "Acceso restringido por el Administrador") ?: "Acceso restringido por el Administrador"
        miApp = appName

        try {
            val db = FirebaseDatabase.getInstance()
            val connectedRef = db.getReference(".info/connected")
            val userStatusRef = db.getReference("presencia_usuarios").child(miUsuarioId)
            databaseRef = userStatusRef

            // Escuchar cambios enviados por el Administrador (nombre, pin, bloqueo, tiempo límite)
            userStatusRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        if (snapshot.exists()) {
                            val adminNombre = snapshot.child("nombre").getValue(String::class.java)
                            val adminPin = snapshot.child("pin").getValue(String::class.java)
                            val adminRol = snapshot.child("rol").getValue(String::class.java)
                            val adminBloqueado = snapshot.child("bloqueado").getValue(Boolean::class.java) ?: false
                            val motivo = snapshot.child("motivoBloqueo").getValue(String::class.java) ?: "Acceso restringido por el Administrador"
                            val tiempoLimite = snapshot.child("tiempoLimiteMillis").getValue(Long::class.java) ?: 0L
                            val tipoTiempo = snapshot.child("tipoTiempoAcceso").getValue(String::class.java) ?: "Indefinido"
                            val fechaFin = snapshot.child("fechaFinAccesoTexto").getValue(String::class.java) ?: "Sin límite"

                            if (!adminNombre.isNullOrEmpty()) {
                                miNombre = adminNombre
                                prefs.edit().putString(KEY_USER_NAME, adminNombre).apply()
                            }
                            if (!adminPin.isNullOrEmpty()) {
                                miPin = adminPin
                                prefs.edit().putString(KEY_USER_PIN, adminPin).apply()
                            }
                            if (!adminRol.isNullOrEmpty()) {
                                miRol = adminRol
                                prefs.edit().putString(KEY_USER_ROLE, adminRol).apply()
                            }

                            estaBloqueadoAdmin = adminBloqueado
                            motivoBloqueoAdmin = motivo
                            miTiempoLimiteMillis = tiempoLimite
                            miTipoTiempoAcceso = tipoTiempo
                            miFechaFinAccesoTexto = fechaFin

                            prefs.edit()
                                .putBoolean(KEY_BLOQUEADO, adminBloqueado)
                                .putString(KEY_MOTIVO_BLOQUEO, motivo)
                                .putLong(KEY_TIEMPO_LIMITE, tiempoLimite)
                                .putString(KEY_TIPO_TIEMPO, tipoTiempo)
                                .putString(KEY_FECHA_FIN, fechaFin)
                                .apply()
                        } else {
                            // Si el nodo aún no existe en Firebase, lo inicializamos
                            val fechaActual = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                            val initData = mapOf(
                                "id" to miUsuarioId,
                                "nombre" to miNombre,
                                "rol" to miRol,
                                "app" to miApp,
                                "dispositivo" to deviceModel,
                                "enLinea" to true,
                                "ultimaConexion" to ServerValue.TIMESTAMP,
                                "ultimaConexionTexto" to fechaActual,
                                "seccionActual" to "Tablero",
                                "pin" to miPin,
                                "bloqueado" to false,
                                "motivoBloqueo" to "Acceso restringido por el Administrador",
                                "tiempoLimiteMillis" to miTiempoLimiteMillis,
                                "tipoTiempoAcceso" to miTipoTiempoAcceso,
                                "fechaFinAccesoTexto" to miFechaFinAccesoTexto
                            )
                            userStatusRef.updateChildren(initData)
                        }

                        val usuarioModel = obtenerUsuarioActualModel()
                        onEstadoCambioListener?.invoke(usuarioModel)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })

            connectedRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val connected = snapshot.getValue(Boolean::class.java) ?: false
                        if (connected) {
                            val fechaActual = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                            val disconnectData = mapOf(
                                "enLinea" to false,
                                "ultimaConexion" to ServerValue.TIMESTAMP,
                                "ultimaConexionTexto" to fechaActual,
                                "seccionActual" to "Desconectado"
                            )
                            try {
                                userStatusRef.onDisconnect().updateChildren(disconnectData)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            val connectData = mapOf(
                                "id" to miUsuarioId,
                                "nombre" to miNombre,
                                "rol" to miRol,
                                "app" to miApp,
                                "dispositivo" to deviceModel,
                                "enLinea" to true,
                                "ultimaConexion" to ServerValue.TIMESTAMP,
                                "ultimaConexionTexto" to fechaActual,
                                "seccionActual" to "Tablero",
                                "pin" to miPin,
                                "tiempoLimiteMillis" to miTiempoLimiteMillis,
                                "tipoTiempoAcceso" to miTipoTiempoAcceso,
                                "fechaFinAccesoTexto" to miFechaFinAccesoTexto
                            )
                            try {
                                userStatusRef.updateChildren(connectData)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun actualizarSeccion(seccion: String) {
        databaseRef?.child("seccionActual")?.setValue(seccion)
        databaseRef?.child("ultimaConexion")?.setValue(ServerValue.TIMESTAMP)
    }
}

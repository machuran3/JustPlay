package com.example.adminjustplay

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object PresenciaManager {

    private const val PREFS_NAME = "justplay_user_prefs"
    private const val KEY_USER_ID = "pref_user_id"
    private const val KEY_USER_NAME = "pref_user_name"
    private const val KEY_USER_ROLE = "pref_user_role"

    private var usuarioActualId: String = ""
    private var nombreUsuarioActual: String = ""
    private var rolUsuarioActual: String = "Administrador"
    private var nombreApp: String = "AdminJustPlay"

    private var isTrackingInitialized = false

    fun obtenerOcrearUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_USER_ID, null)
        if (id.isNullOrEmpty()) {
            id = UUID.randomUUID().toString().take(8)
            prefs.edit().putString(KEY_USER_ID, id).apply()
        }
        usuarioActualId = id
        return id
    }

    fun obtenerNombreUsuario(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        nombreUsuarioActual = prefs.getString(KEY_USER_NAME, "") ?: ""
        return nombreUsuarioActual
    }

    fun guardarNombreUsuario(context: Context, nombre: String, rol: String = "Administrador") {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_USER_NAME, nombre.trim())
            .putString(KEY_USER_ROLE, rol)
            .apply()
        nombreUsuarioActual = nombre.trim()
        rolUsuarioActual = rol
        actualizarPresenciaEnFirebase(true)
    }

    fun tieneNombreConfigurado(context: Context): Boolean {
        return obtenerNombreUsuario(context).isNotBlank()
    }

    fun iniciarMonitoreoPresencia(context: Context, nombreApp: String = "AdminJustPlay", rolPorDefecto: String = "Administrador") {
        this.nombreApp = nombreApp
        this.rolUsuarioActual = rolPorDefecto
        obtenerOcrearUserId(context)
        obtenerNombreUsuario(context)

        if (isTrackingInitialized) return
        isTrackingInitialized = true

        val database = FirebaseDatabase.getInstance()
        val connectedRef = database.getReference(".info/connected")
        val userRef = database.getReference("presencia_usuarios").child(usuarioActualId)

        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    val dispositivo = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
                    val fechaActual = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

                    val datosDesconexion = mapOf(
                        "enLinea" to false,
                        "ultimaConexion" to ServerValue.TIMESTAMP,
                        "ultimaConexionTexto" to fechaActual,
                        "seccionActual" to "Desconectado"
                    )
                    userRef.onDisconnect().updateChildren(datosDesconexion)

                    val datosConexion = mapOf(
                        "id" to usuarioActualId,
                        "nombre" to if (nombreUsuarioActual.isNotBlank()) nombreUsuarioActual else "Usuario $usuarioActualId",
                        "rol" to rolUsuarioActual,
                        "app" to nombreApp,
                        "enLinea" to true,
                        "ultimaConexion" to ServerValue.TIMESTAMP,
                        "ultimaConexionTexto" to fechaActual,
                        "dispositivo" to dispositivo,
                        "seccionActual" to "Tablero"
                    )
                    userRef.updateChildren(datosConexion)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun actualizarSeccion(seccion: String) {
        if (usuarioActualId.isEmpty()) return
        try {
            val userRef = FirebaseDatabase.getInstance().getReference("presencia_usuarios").child(usuarioActualId)
            userRef.child("seccionActual").setValue(seccion)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun actualizarPresenciaEnFirebase(enLinea: Boolean) {
        if (usuarioActualId.isEmpty()) return
        try {
            val userRef = FirebaseDatabase.getInstance().getReference("presencia_usuarios").child(usuarioActualId)
            val fechaActual = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            val dispositivo = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

            val datos = mapOf(
                "id" to usuarioActualId,
                "nombre" to if (nombreUsuarioActual.isNotBlank()) nombreUsuarioActual else "Usuario $usuarioActualId",
                "rol" to rolUsuarioActual,
                "app" to nombreApp,
                "enLinea" to enLinea,
                "ultimaConexion" to ServerValue.TIMESTAMP,
                "ultimaConexionTexto" to fechaActual,
                "dispositivo" to dispositivo
            )
            userRef.updateChildren(datos)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

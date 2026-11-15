package com.example.minicine01

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JustPlayApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Escudo global de excepciones: previene cierres silenciosos y registra trazabilidad completa
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("JustPlayCrash", "FATAL CRASH in thread ${thread.name}", throwable)
                val stackTrace = Log.getStackTraceString(throwable)

                // 1. Guardar copia local en almacenamiento interno
                try {
                    val file = File(filesDir, "crash_log.txt")
                    val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                    file.writeText("Fecha: $fecha\nHilo: ${thread.name}\nDispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})\nError: ${throwable.message}\n\n$stackTrace")
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 2. Reportar inmediatamente a Firebase Realtime Database
                try {
                    val crashData = mapOf(
                        "app" to "Employ_JustPlay",
                        "timestamp" to System.currentTimeMillis(),
                        "fecha" to SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
                        "hilo" to thread.name,
                        "dispositivo" to "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})",
                        "errorClase" to throwable.javaClass.name,
                        "mensaje" to (throwable.message ?: "Sin mensaje"),
                        "stackTrace" to stackTrace
                    )
                    FirebaseDatabase.getInstance().getReference("crashes").child("Employ_JustPlay").push().setValue(crashData)
                    Thread.sleep(1200) // Breve pausa para dar tiempo al socket de red
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 3. Abrir pantalla de error para información al usuario
                try {
                    val intent = Intent(this@JustPlayApp, CrashDisplayActivity::class.java).apply {
                        putExtra("EXTRA_CRASH_INFO", "Error: ${throwable.javaClass.simpleName}: ${throwable.message}\n\nHilo: ${thread.name}\nDispositivo: ${Build.MANUFACTURER} ${Build.MODEL}\n\n$stackTrace")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    Process.killProcess(Process.myPid())
                    System.exit(10)
                    return@setDefaultUncaughtExceptionHandler
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            // Habilita persistencia offline en disco para ver datos sin internet
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

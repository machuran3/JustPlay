package com.example.minicine01

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class JustPlayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Habilita el guardado en caché local en disco para ver salas, clientes y tiempos sin conexión a internet
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

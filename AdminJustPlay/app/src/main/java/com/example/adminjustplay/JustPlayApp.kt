package com.example.adminjustplay

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class JustPlayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Habilita persistencia offline en disco para ver datos sin internet
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

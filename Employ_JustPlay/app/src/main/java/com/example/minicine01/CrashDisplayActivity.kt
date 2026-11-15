package com.example.minicine01

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Pantalla de recuperación y diagnóstico de fallos críticos para Employ_JustPlay.
 * Evita que la app se cierre en negro o al inicio sin explicación al usuario.
 */
class CrashDisplayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashInfo = intent.getStringExtra("EXTRA_CRASH_INFO") ?: "Error desconocido en la aplicación."

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#12141D"))
            setPadding(32, 48, 32, 48)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tvIcono = TextView(this).apply {
            text = "🛡️"
            textSize = 44f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        container.addView(tvIcono)

        val tvTitulo = TextView(this).apply {
            text = "Employ JustPlay • Escudo de Diagnóstico"
            setTextColor(Color.parseColor("#DEFF9A"))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        container.addView(tvTitulo)

        val tvExplicacion = TextView(this).apply {
            text = "Se ha interceptado una excepción al ejecutar la app. El reporte ya ha sido registrado automáticamente en Firebase."
            setTextColor(Color.parseColor("#D0D0D0"))
            textSize = 14f
            setPadding(0, 0, 0, 20)
        }
        container.addView(tvExplicacion)

        val tvLog = TextView(this).apply {
            text = crashInfo
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setBackgroundColor(Color.parseColor("#1E2130"))
            setPadding(24, 24, 24, 24)
        }
        container.addView(tvLog)

        val btnCopiar = Button(this).apply {
            text = "📋 Copiar Reporte de Error"
            setBackgroundColor(Color.parseColor("#2E344E"))
            setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 32, 0, 16)
            layoutParams = lp
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Crash Log", crashInfo)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@CrashDisplayActivity, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(btnCopiar)

        val btnReintentar = Button(this).apply {
            text = "🔄 Reintentar Abrir la Aplicación"
            setBackgroundColor(Color.parseColor("#DEFF9A"))
            setTextColor(Color.BLACK)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 16)
            layoutParams = lp
            setOnClickListener {
                val intent = Intent(this@CrashDisplayActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }
        container.addView(btnReintentar)

        scrollView.addView(container)
        setContentView(scrollView)
    }
}

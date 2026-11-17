package com.example.adminjustplay

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateManager {

    data class ActualizacionInfo(
        val hayNuevaVersion: Boolean,
        val versionActual: String,
        val versionNueva: String,
        val novedades: String,
        val urlDescargaApk: String,
        val esObligatoria: Boolean = false
    )

    fun verificarActualizacion(
        activity: Activity,
        nombreApp: String = "AdminJustPlay",
        githubOwnerRepo: String? = "machuran3/JustPlay",
        mostrarToastSiNoHay: Boolean = false
    ) {
        val versionActual = obtenerVersionAppActual(activity)

        val dbRef = FirebaseDatabase.getInstance().getReference("config_actualizaciones").child(nombreApp)
        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val versionNueva = snapshot.child("versionName").getValue(String::class.java) ?: ""
                    val urlApk = snapshot.child("apkUrl").getValue(String::class.java) ?: ""
                    val novedades = snapshot.child("novedades").getValue(String::class.java) ?: "Mejoras de estabilidad y nuevas funciones."
                    val esObligatoria = snapshot.child("esObligatoria").getValue(Boolean::class.java) ?: false
                    val repoGitHub = snapshot.child("githubOwnerRepo").getValue(String::class.java) ?: githubOwnerRepo

                    if (versionNueva.isNotEmpty() && esVersionSuperior(versionNueva, versionActual)) {
                        val info = ActualizacionInfo(
                            hayNuevaVersion = true,
                            versionActual = versionActual,
                            versionNueva = versionNueva,
                            novedades = novedades,
                            urlDescargaApk = urlApk,
                            esObligatoria = esObligatoria
                        )
                        activity.runOnUiThread { mostrarDialogoActualizacion(activity, info) }
                        return
                    }

                    if (!repoGitHub.isNullOrEmpty()) {
                        consultarGitHubReleases(activity, repoGitHub, versionActual, mostrarToastSiNoHay)
                        return
                    }
                } else if (!githubOwnerRepo.isNullOrEmpty()) {
                    consultarGitHubReleases(activity, githubOwnerRepo, versionActual, mostrarToastSiNoHay)
                    return
                }

                if (mostrarToastSiNoHay) {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "✅ Tu aplicación está actualizada (v$versionActual)", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (!githubOwnerRepo.isNullOrEmpty()) {
                    consultarGitHubReleases(activity, githubOwnerRepo, versionActual, mostrarToastSiNoHay)
                } else if (mostrarToastSiNoHay) {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "No se pudo comprobar actualizaciones en este momento.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    fun consultarGitHubReleases(
        activity: Activity,
        githubOwnerRepo: String,
        versionActual: String,
        mostrarToastSiNoHay: Boolean = false
    ) {
        Thread {
            try {
                val cleanRepo = githubOwnerRepo.replace("https://github.com/", "").trim().removeSuffix("/")
                val apiUrl = "https://api.github.com/repos/$cleanRepo/releases/latest"
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "JustPlay-InAppUpdater")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseStr)

                    val tagName = json.optString("tag_name", "").removePrefix("v").removePrefix("V").trim()
                    val body = json.optString("body", "Nuevas mejoras disponibles.")
                    val htmlUrl = json.optString("html_url", "")

                    var apkDownloadUrl = ""
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true) && name.contains("Admin", ignoreCase = true)) {
                                apkDownloadUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                        if (apkDownloadUrl.isEmpty()) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.optString("name", "")
                                if (name.endsWith(".apk", ignoreCase = true)) {
                                    apkDownloadUrl = asset.optString("browser_download_url", "")
                                    break
                                }
                            }
                        }
                    }

                    if (apkDownloadUrl.isEmpty()) {
                        apkDownloadUrl = htmlUrl
                    }

                    if (tagName.isNotEmpty() && esVersionSuperior(tagName, versionActual)) {
                        val info = ActualizacionInfo(
                            hayNuevaVersion = true,
                            versionActual = versionActual,
                            versionNueva = tagName,
                            novedades = body,
                            urlDescargaApk = apkDownloadUrl,
                            esObligatoria = false
                        )
                        activity.runOnUiThread { mostrarDialogoActualizacion(activity, info) }
                        return@Thread
                    }
                }

                if (mostrarToastSiNoHay) {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "✅ Tu aplicación está en la última versión (v$versionActual)", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (mostrarToastSiNoHay) {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "No se pudo conectar con el servidor de actualizaciones.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    fun mostrarDialogoActualizacion(activity: Activity, info: ActualizacionInfo) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_actualizacion_app, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(!info.esObligatoria)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvVersiones = dialogView.findViewById<TextView>(R.id.tvUpdateVersiones)
        val tvNovedades = dialogView.findViewById<TextView>(R.id.tvUpdateNovedades)
        val pbDescarga = dialogView.findViewById<ProgressBar>(R.id.pbUpdateDescarga)
        val tvEstadoDescarga = dialogView.findViewById<TextView>(R.id.tvUpdateEstadoDescarga)
        val btnDescargarInstalar = dialogView.findViewById<Button>(R.id.btnUpdateDescargarInstalar)
        val btnNavegador = dialogView.findViewById<Button>(R.id.btnUpdateNavegador)
        val btnMasTarde = dialogView.findViewById<Button>(R.id.btnUpdateMasTarde)

        tvVersiones.text = "v${info.versionActual}  ➔  v${info.versionNueva}"
        tvNovedades.text = if (info.novedades.isNotBlank()) info.novedades else "• Corrección de errores y optimizaciones generales."

        if (info.esObligatoria) {
            btnMasTarde.visibility = View.GONE
        } else {
            btnMasTarde.setOnClickListener { dialog.dismiss() }
        }

        btnNavegador.setOnClickListener {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.urlDescargaApk))
                activity.startActivity(browserIntent)
            } catch (e: Exception) {
                Toast.makeText(activity, "No se pudo abrir el navegador", Toast.LENGTH_SHORT).show()
            }
        }

        btnDescargarInstalar.setOnClickListener {
            if (info.urlDescargaApk.endsWith(".apk", ignoreCase = true)) {
                btnDescargarInstalar.isEnabled = false
                btnDescargarInstalar.text = "⏳ Descargando..."
                pbDescarga.visibility = View.VISIBLE
                tvEstadoDescarga.visibility = View.VISIBLE

                descargarEInstalarApk(activity, info.urlDescargaApk, info.versionNueva, pbDescarga, tvEstadoDescarga) { exito ->
                    if (!exito) {
                        btnDescargarInstalar.isEnabled = true
                        btnDescargarInstalar.text = "⬇️ Reintentar Descarga"
                    }
                }
            } else {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.urlDescargaApk))
                    activity.startActivity(browserIntent)
                } catch (_: Exception) {}
            }
        }

        dialog.show()
    }

    private fun descargarEInstalarApk(
        activity: Activity,
        apkUrl: String,
        versionNueva: String,
        progressBar: ProgressBar,
        tvEstado: TextView,
        onResultado: (Boolean) -> Unit
    ) {
        Thread {
            try {
                val url = URL(apkUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.connect()

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Error al conectar con el servidor de descarga (${conn.responseCode})", Toast.LENGTH_LONG).show()
                        onResultado(false)
                    }
                    return@Thread
                }

                val fileLength = conn.contentLength
                val downloadsDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.filesDir
                val apkFile = File(downloadsDir, "justplay-admin-update-v$versionNueva.apk")
                if (apkFile.exists()) apkFile.delete()

                val input = conn.inputStream
                val output = FileOutputStream(apkFile)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int

                while (input.read(data).also { count = it } != -1) {
                    total += count.toLong()
                    if (fileLength > 0) {
                        val progress = (total * 100 / fileLength).toInt()
                        activity.runOnUiThread {
                            progressBar.progress = progress
                            tvEstado.text = "Descargando: $progress% (${total / (1024 * 1024)}MB / ${fileLength / (1024 * 1024)}MB)"
                        }
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                activity.runOnUiThread {
                    tvEstado.text = "✅ Descarga completa. Abriendo instalador..."
                    instalarApkDescargado(activity, apkFile)
                    onResultado(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                activity.runOnUiThread {
                    tvEstado.text = "❌ Error en la descarga. Usa el botón de navegador."
                    Toast.makeText(activity, "Error de descarga: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    onResultado(false)
                }
            }
        }.start()
    }

    fun instalarApkDescargado(activity: Activity, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(activity, "Por favor autoriza instalar actualizaciones para Admin Just Play", Toast.LENGTH_LONG).show()
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivity(settingsIntent)
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, "No se pudo iniciar el instalador: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun obtenerVersionAppActual(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    fun esVersionSuperior(versionOnline: String, versionLocal: String): Boolean {
        try {
            val partesOnline = versionOnline.removePrefix("v").removePrefix("V").trim().split(".")
            val partesLocal = versionLocal.removePrefix("v").removePrefix("V").trim().split(".")

            val maxLen = maxOf(partesOnline.size, partesLocal.size)
            for (i in 0 until maxLen) {
                val numOnline = partesOnline.getOrNull(i)?.toIntOrNull() ?: 0
                val numLocal = partesLocal.getOrNull(i)?.toIntOrNull() ?: 0

                if (numOnline > numLocal) return true
                if (numOnline < numLocal) return false
            }
            return false
        } catch (e: Exception) {
            return versionOnline > versionLocal
        }
    }
}

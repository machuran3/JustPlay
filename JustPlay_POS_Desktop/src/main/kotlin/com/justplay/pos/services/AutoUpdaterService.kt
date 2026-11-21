package com.justplay.pos.services

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val downloadUrl: String,
    val assetName: String,
    val assetSize: Long,
    val htmlUrl: String
)

class AutoUpdaterService(
    private val githubRepo: String = "machuran3/JustPlay",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val CURRENT_APP_VERSION = "1.0.6"
    }

    /**
     * Compara dos cadenas de versión semántica (ej. "1.0.1" vs "1.0.0").
     * Retorna positivo si candidate > current.
     */
    fun isNewerVersion(candidate: String, current: String): Boolean {
        val cleanCandidate = candidate.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = current.trim().removePrefix("v").removePrefix("V")

        val cParts = cleanCandidate.split(".").mapNotNull { it.toIntOrNull() }
        val curParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(cParts.size, curParts.size)
        for (i in 0 until maxLen) {
            val c = cParts.getOrElse(i) { 0 }
            val cur = curParts.getOrElse(i) { 0 }
            if (c > cur) return true
            if (c < cur) return false
        }
        return false
    }

    /**
     * Consulta la API pública de GitHub para obtener la última versión disponible.
     */
    suspend fun checkForUpdates(currentVersion: String = CURRENT_APP_VERSION): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$githubRepo/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "JustPlayPOS-Desktop-Updater")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return@withContext Result.success(null) // No hay releases creados aún
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Error al consultar GitHub (HTTP ${response.code})"))
                }

                val bodyStr = response.body?.string() ?: return@withContext Result.success(null)
                val json = JsonParser.parseString(bodyStr).asJsonObject

                val tagName = json.get("tag_name")?.asString ?: ""
                val versionName = tagName.removePrefix("v").removePrefix("V")
                val title = json.get("name")?.asString ?: "Versión $tagName"
                val changelog = json.get("body")?.asString ?: "Sin notas de versión."
                val htmlUrl = json.get("html_url")?.asString ?: "https://github.com/$githubRepo/releases"

                if (!isNewerVersion(versionName, currentVersion)) {
                    return@withContext Result.success(null) // Ya está actualizado
                }

                // Buscar asset prioritario: .exe, .jar o .zip
                var downloadUrl = ""
                var assetName = ""
                var assetSize = 0L

                val assets = json.getAsJsonArray("assets")
                if (assets != null && assets.size() > 0) {
                    for (assetElement in assets) {
                        val assetObj = assetElement.asJsonObject
                        val name = assetObj.get("name")?.asString ?: ""
                        if (name.endsWith(".exe", ignoreCase = true) ||
                            name.endsWith(".jar", ignoreCase = true) ||
                            name.endsWith(".zip", ignoreCase = true)
                        ) {
                            downloadUrl = assetObj.get("browser_download_url")?.asString ?: ""
                            assetName = name
                            assetSize = assetObj.get("size")?.asLong ?: 0L
                            break
                        }
                    }
                    // Si no encontró por extensión, usar el primer asset
                    if (downloadUrl.isBlank()) {
                        val first = assets.get(0).asJsonObject
                        downloadUrl = first.get("browser_download_url")?.asString ?: ""
                        assetName = first.get("name")?.asString ?: ""
                        assetSize = first.get("size")?.asLong ?: 0L
                    }
                }

                if (downloadUrl.isBlank()) {
                    downloadUrl = htmlUrl
                    assetName = "Actualización Manual (GitHub)"
                }

                val release = ReleaseInfo(
                    tagName = tagName,
                    versionName = versionName,
                    title = title,
                    changelog = changelog,
                    downloadUrl = downloadUrl,
                    assetName = assetName,
                    assetSize = assetSize,
                    htmlUrl = htmlUrl
                )
                Result.success(release)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Descarga el archivo de actualización informando el progreso (0.0 a 1.0).
     */
    suspend fun downloadAsset(
        downloadUrl: String,
        targetFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            targetFile.parentFile?.mkdirs()
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "JustPlayPOS-Desktop-Updater")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Error al descargar archivo (HTTP ${response.code})"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("Cuerpo de respuesta vacío"))
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedBytes = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                onProgress(downloadedBytes.toFloat() / totalBytes)
                            }
                        }
                        output.flush()
                    }
                }
            }
            onProgress(1.0f)
            Result.success(targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Ejecuta el instalador descargado o abre la carpeta con la nueva versión.
     */
    fun openOrInstallUpdate(file: File) {
        try {
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("win") && file.name.endsWith(".exe", ignoreCase = true) -> {
                    ProcessBuilder(file.absolutePath).start()
                }
                os.contains("win") && file.name.endsWith(".jar", ignoreCase = true) -> {
                    ProcessBuilder("java", "-jar", file.absolutePath).start()
                }
                else -> {
                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().open(file.parentFile ?: file)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

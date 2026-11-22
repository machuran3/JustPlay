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
        const val CURRENT_APP_VERSION = "1.0.9"
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

                // Buscar asset prioritario: JustPlayPOS.jar
                var downloadUrl = ""
                var assetName = ""
                var assetSize = 0L

                val assets = json.getAsJsonArray("assets")
                if (assets != null && assets.size() > 0) {
                    // 1. Priorizar JustPlayPOS.jar
                    for (assetElement in assets) {
                        val assetObj = assetElement.asJsonObject
                        val name = assetObj.get("name")?.asString ?: ""
                        if (name.contains("JustPlayPOS", ignoreCase = true) && name.endsWith(".jar", ignoreCase = true)) {
                            downloadUrl = assetObj.get("browser_download_url")?.asString ?: ""
                            assetName = name
                            assetSize = assetObj.get("size")?.asLong ?: 0L
                            break
                        }
                    }
                    // 2. Si no encontró por nombre JustPlayPOS, cualquier .jar o .exe
                    if (downloadUrl.isBlank()) {
                        for (assetElement in assets) {
                            val assetObj = assetElement.asJsonObject
                            val name = assetObj.get("name")?.asString ?: ""
                            if (name.endsWith(".jar", ignoreCase = true) ||
                                name.endsWith(".exe", ignoreCase = true) ||
                                name.endsWith(".zip", ignoreCase = true)
                            ) {
                                downloadUrl = assetObj.get("browser_download_url")?.asString ?: ""
                                assetName = name
                                assetSize = assetObj.get("size")?.asLong ?: 0L
                                break
                            }
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
     * Aplica la actualización y reinicia la aplicación automáticamente en 1-Clic.
     */
    fun applyUpdateAndRestart(downloadedFile: File): Result<Boolean> {
        try {
            val os = System.getProperty("os.name").lowercase()

            // 1. Determinar la ubicación del JAR en ejecución
            val runningJar = try {
                val codeSource = AutoUpdaterService::class.java.protectionDomain.codeSource
                val location = codeSource?.location?.toURI()?.path
                if (location != null && location.endsWith(".jar", ignoreCase = true)) {
                    File(location)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }

            // Ubicaciones estándar donde puede estar instalado JustPlayPOS
            val userHome = System.getProperty("user.home")
            val standardLinuxInstall = File(userHome, ".local/share/justplay/JustPlayPOS.jar")
            val repoReleaseAsset = File("build/release_assets/JustPlayPOS.jar")

            val targetJar = when {
                runningJar != null && runningJar.isFile -> runningJar
                standardLinuxInstall.exists() -> standardLinuxInstall
                repoReleaseAsset.exists() -> repoReleaseAsset.absoluteFile
                else -> standardLinuxInstall
            }

            targetJar.parentFile?.mkdirs()

            if (os.contains("win")) {
                // Windows: script .bat para esperar que termine el proceso, fusionar/reemplazar y reabrir
                val targetDir = targetJar.parentFile?.absolutePath ?: userHome
                val updaterScript = File.createTempFile("justplay_update_", ".bat")
                val batContent = buildString {
                    appendLine("@echo off")
                    appendLine("timeout /t 2 /nobreak > nul")
                    appendLine("copy /y \"${downloadedFile.absolutePath}\" \"${targetJar.absolutePath}\"")
                    appendLine("del \"${downloadedFile.absolutePath}\"")
                    appendLine("del \"%USERPROFILE%\\.justplay_pos\\updates\\*.jar\" 2>nul")
                    appendLine("echo Set WshShell = CreateObject(\"WScript.Shell\") > \"$targetDir\\JustPlay.vbs\"")
                    appendLine("echo WshShell.Run \"javaw -Xmx2048m -jar \"\"${targetJar.name}\"\"\", 0, False >> \"$targetDir\\JustPlay.vbs\"")
                    appendLine("powershell -Command \"\$ws = New-Object -ComObject WScript.Shell; \$s = \$ws.CreateShortcut([Environment]::GetFolderPath('Desktop') + '\\JustPlay POS.lnk'); \$s.TargetPath = 'wscript.exe'; \$s.Arguments = '\"\"$targetDir\\JustPlay.vbs\"\"'; \$s.WorkingDirectory = '$targetDir'; \$s.Save()\" 2>nul")
                    appendLine("start \"\" javaw -Xmx2048m -jar \"${targetJar.absolutePath}\"")
                    appendLine("del \"%~f0\"")
                }
                updaterScript.writeText(batContent)
                ProcessBuilder("cmd.exe", "/c", updaterScript.absolutePath).start()
                kotlin.system.exitProcess(0)
            } else {
                // Linux / macOS: script bash para fusionar/reemplazar, borrar residuales y asegurar icono
                val updaterScript = File.createTempFile("justplay_update_", ".sh")
                val scriptContent = """
                    #!/bin/bash
                    sleep 1
                    cp -f "${downloadedFile.absolutePath}" "${targetJar.absolutePath}"
                    chmod +x "${targetJar.absolutePath}"
                    
                    STD_PATH="$userHome/.local/share/justplay/JustPlayPOS.jar"
                    if [ -d "$userHome/.local/share/justplay" ] && [ "${targetJar.absolutePath}" != "${'$'}STD_PATH" ]; then
                        cp -f "${downloadedFile.absolutePath}" "${'$'}STD_PATH"
                        chmod +x "${'$'}STD_PATH"
                    fi
                    
                    # Eliminar la versión descargada temporal y residuales para no duplicar archivos
                    rm -f "${downloadedFile.absolutePath}"
                    rm -f "$userHome/.justplay_pos/updates/"*.jar 2>/dev/null
                    
                    # Asegurar acceso directo con icono en el Escritorio
                    APP_DIR="$userHome/.local/share/justplay"
                    DESKTOP_DIR=""
                    [ -d "$userHome/Desktop" ] && DESKTOP_DIR="$userHome/Desktop"
                    [ -d "$userHome/Escritorio" ] && DESKTOP_DIR="$userHome/Escritorio"
                    
                    if [ -n "${'$'}DESKTOP_DIR" ]; then
                        ICON_PATH="${'$'}APP_DIR/icon.png"
                        [ ! -f "${'$'}ICON_PATH" ] && ICON_PATH="/home/maru/Android/JustPlay/justplay_icon.png"
                        EXEC_JAR="${'$'}STD_PATH"
                        [ ! -f "${'$'}EXEC_JAR" ] && EXEC_JAR="${targetJar.absolutePath}"
                        cat <<EOF > "${'$'}DESKTOP_DIR/JustPlayPOS.desktop"
[Desktop Entry]
Version=1.0
Type=Application
Name=JustPlay POS
GenericName=Punto de Venta y Gestión de Salas
Comment=JustPlay POS Scanner & Gestión de Salas
Exec=java -Xmx2048m -jar "${'$'}EXEC_JAR"
Icon=${'$'}ICON_PATH
Terminal=false
Categories=Office;Finance;Utility;
StartupNotify=true
StartupWMClass=JustPlayPOS
EOF
                        chmod +x "${'$'}DESKTOP_DIR/JustPlayPOS.desktop"
                        gio set "${'$'}DESKTOP_DIR/JustPlayPOS.desktop" metadata::trusted true 2>/dev/null || true
                    fi
                    
                    nohup java -Xmx2048m -jar "${targetJar.absolutePath}" >/dev/null 2>&1 &
                    rm -f "$0"
                """.trimIndent()

                updaterScript.writeText(scriptContent)
                updaterScript.setExecutable(true)
                ProcessBuilder("bash", updaterScript.absolutePath).start()
                kotlin.system.exitProcess(0)
            }
            return Result.success(true)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    /**
     * Ejecuta el instalador descargado o abre la carpeta con la nueva versión (fallback).
     */
    fun openOrInstallUpdate(file: File) {
        applyUpdateAndRestart(file)
    }
}

package com.justplay.pos.infrastructure.security

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class DeviceIdentity(
    val deviceId: String,
    val hardwareUuid: String,
    var pcName: String,
    val hostname: String,
    val osName: String,
    var sessionToken: String,
    var isAuthorized: Boolean = true
)

class UpgradeRequiredException(message: String) : RuntimeException(message)
class UnauthorizedException(message: String) : RuntimeException(message)

/**
 * Servicio de Seguridad, Identidad de Hardware, Control de Versión (Force Update)
 * y Heartbeat en tiempo real para JustPlay POS Desktop.
 */
class DeviceSecurityService(
    private var baseUrl: String = "https://justplay-a081e-default-rtdb.firebaseio.com",
    private val appVersion: String = "1.0.0",
    private val configDir: File = File(System.getProperty("user.home"), ".justplay_pos"),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val identityFile = File(configDir, "device_identity.json")
    val identity: DeviceIdentity

    private val _isDeviceRevokedFlow = MutableStateFlow(false)
    val isDeviceRevokedFlow: StateFlow<Boolean> = _isDeviceRevokedFlow.asStateFlow()

    private val _isVersionObsoleteFlow = MutableStateFlow(false)
    val isVersionObsoleteFlow: StateFlow<Boolean> = _isVersionObsoleteFlow.asStateFlow()

    private val _minRequiredVersionFlow = MutableStateFlow("1.0.0")
    val minRequiredVersionFlow: StateFlow<String> = _minRequiredVersionFlow.asStateFlow()

    private var heartbeatJob: Job? = null

    init {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        identity = loadOrCreateIdentity()

        // Shutdown hook para marcar OFFLINE al salir
        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                sendOfflineStatusSync()
            } catch (_: Exception) {}
        })
    }

    private fun loadOrCreateIdentity(): DeviceIdentity {
        if (identityFile.exists()) {
            try {
                val json = identityFile.readText()
                val id = gson.fromJson(json, DeviceIdentity::class.java)
                if (id != null && id.deviceId.isNotBlank()) {
                    return id
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Generar nueva identidad de hardware
        val hostname = try { InetAddress.getLocalHost().hostName } catch (_: Exception) { "PC-UNKNOWN" }
        val os = "${System.getProperty("os.name")} ${System.getProperty("os.arch")}"
        val hwFingerprint = generateHardwareFingerprint(hostname, os)
        val shortId = "POS-PC-" + hwFingerprint.take(8).uppercase(Locale.ROOT)
        val initialToken = "TOK_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        val newIdentity = DeviceIdentity(
            deviceId = shortId,
            hardwareUuid = hwFingerprint,
            pcName = "Caja $hostname",
            hostname = hostname,
            osName = os,
            sessionToken = initialToken,
            isAuthorized = true
        )

        saveIdentity(newIdentity)
        return newIdentity
    }

    fun saveIdentity(id: DeviceIdentity) {
        try {
            identityFile.writeText(gson.toJson(id))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateHardwareFingerprint(hostname: String, os: String): String {
        return try {
            val user = System.getProperty("user.name") ?: "user"
            val sb = StringBuilder("$hostname|$os|$user")
            
            // Intentar obtener MAC Address de interfaces de red
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val mac = ni.hardwareAddress
                if (mac != null && mac.isNotEmpty()) {
                    sb.append("|").append(mac.joinToString(":") { "%02X".format(it) })
                    break
                }
            }

            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(sb.toString().toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            UUID.randomUUID().toString()
        }
    }

    /**
     * Inicia el latido (Heartbeat / Ping) periódico cada 30 segundos.
     */
    fun startHeartbeat() {
        if (heartbeatJob != null && heartbeatJob?.isActive == true) return

        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    sendHeartbeat()
                    checkMinVersion()
                } catch (e: Exception) {
                    // Fallo de red temporal
                }
                delay(30000) // 30 segundos
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    suspend fun refreshSecurityStatus() = withContext(Dispatchers.IO) {
        try {
            sendHeartbeat()
            checkMinVersion()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun sendHeartbeat() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val nowStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(now))
        val ip = try { InetAddress.getLocalHost().hostAddress } catch (_: Exception) { "127.0.0.1" }

        val payload = mapOf(
            "deviceId" to identity.deviceId,
            "hardwareUuid" to identity.hardwareUuid,
            "nombre" to identity.pcName,
            "hostname" to identity.hostname,
            "osName" to identity.osName,
            "appVersion" to appVersion,
            "ipAddress" to ip,
            "lastHeartbeat" to now,
            "lastHeartbeatStr" to nowStr,
            "estadoConexion" to "ONLINE",
            "sessionToken" to identity.sessionToken,
            "autorizado" to identity.isAuthorized
        )

        val jsonBody = gson.toJson(payload).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/terminales_pos/${identity.deviceId}.json")
            .patch(jsonBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                // Verificar si el estado fue revocado en Firebase
                verifyTerminalAuthorization()
            }
        }
    }

    private suspend fun verifyTerminalAuthorization() = withContext(Dispatchers.IO) {
        try {
            // 1. Obtener configuración global de config_pos
            val globalReq = Request.Builder()
                .url("$baseUrl/config_pos.json")
                .get()
                .build()

            var posHabilitadoGlobal = true
            var permitirTodasLasPc = true

            client.newCall(globalReq).execute().use { gResp ->
                if (gResp.isSuccessful) {
                    val gBody = gResp.body?.string()?.trim()
                    if (gBody != null && gBody != "null") {
                        val mapType = object : TypeToken<Map<String, Any?>>() {}.type
                        val configMap: Map<String, Any?> = gson.fromJson(gBody, mapType) ?: emptyMap()
                        posHabilitadoGlobal = configMap["habilitado"] as? Boolean
                            ?: configMap["pos_habilitado"] as? Boolean ?: true
                        permitirTodasLasPc = configMap["permitir_todas_las_pc"] as? Boolean ?: true
                    }
                }
            }

            if (!posHabilitadoGlobal) {
                _isDeviceRevokedFlow.value = true
                return@withContext
            }

            // 2. Verificar autorización de esta terminal individual
            val request = Request.Builder()
                .url("$baseUrl/terminales_pos/${identity.deviceId}.json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null && body != "null") {
                        val mapType = object : TypeToken<Map<String, Any?>>() {}.type
                        val data: Map<String, Any?> = gson.fromJson(body, mapType) ?: emptyMap()

                        val auth = data["autorizado"] as? Boolean ?: true
                        val ventasHabilitadas = data["ventasHabilitadas"] as? Boolean ?: auth
                        val estado = data["estadoConexion"] as? String ?: "ONLINE"
                        val remoteToken = data["sessionToken"] as? String ?: ""

                        val isPermitted = if (permitirTodasLasPc) {
                            auth && estado != "DESVINCULADO"
                        } else {
                            auth && ventasHabilitadas && estado != "DESVINCULADO"
                        }

                        if (!isPermitted || (remoteToken.isNotBlank() && remoteToken != identity.sessionToken)) {
                            _isDeviceRevokedFlow.value = true
                            identity.isAuthorized = false
                            saveIdentity(identity)
                        } else {
                            _isDeviceRevokedFlow.value = false
                            identity.isAuthorized = true
                            if (remoteToken.isNotBlank() && identity.sessionToken != remoteToken) {
                                identity.sessionToken = remoteToken
                                saveIdentity(identity)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun checkMinVersion() = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/config_pos/min_app_version_desktop.json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val minVer = response.body?.string()?.replace("\"", "")?.trim() ?: "1.0.0"
                    _minRequiredVersionFlow.value = minVer
                    val isObsolete = compareVersions(appVersion, minVer) < 0
                    _isVersionObsoleteFlow.value = isObsolete
                }
            }
        } catch (_: Exception) {}
    }

    private fun sendOfflineStatusSync() {
        try {
            val payload = mapOf("estadoConexion" to "OFFLINE")
            val jsonBody = gson.toJson(payload).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/terminales_pos/${identity.deviceId}.json")
                .patch(jsonBody)
                .build()
            client.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    /**
     * AuthGuard: Valida que el cliente tenga una versión compatible (426)
     * y un token de dispositivo autorizado (401).
     */
    fun validateAuthGuard(endpoint: String = "") {
        if (_isVersionObsoleteFlow.value) {
            throw UpgradeRequiredException("HTTP 426 Upgrade Required: Tu versión de JustPlay POS (v$appVersion) está obsoleta. Es obligatorio actualizar a v${_minRequiredVersionFlow.value}.")
        }
        if (_isDeviceRevokedFlow.value || !identity.isAuthorized) {
            throw UnauthorizedException("HTTP 401 Unauthorized: Esta terminal POS (${identity.deviceId}) ha sido desvinculada o no está autorizada por el Administrador.")
        }
    }

    fun reauthorizeLocally(newToken: String) {
        identity.sessionToken = newToken
        identity.isAuthorized = true
        _isDeviceRevokedFlow.value = false
        saveIdentity(identity)
        scope.launch { sendHeartbeat() }
    }

    companion object {
        /**
         * Comparador semántico de versiones (e.g. "1.0.1" vs "1.1.0").
         * Retorna < 0 si v1 < v2, 0 si v1 == v2, > 0 si v1 > v2.
         */
        fun compareVersions(v1: String, v2: String): Int {
            val parts1 = v1.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            val parts2 = v2.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(parts1.size, parts2.size)

            for (i in 0 until maxLen) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) {
                    return p1.compareTo(p2)
                }
            }
            return 0
        }
    }
}

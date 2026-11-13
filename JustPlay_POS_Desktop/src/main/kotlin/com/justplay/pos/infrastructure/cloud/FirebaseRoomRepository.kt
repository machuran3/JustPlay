package com.justplay.pos.infrastructure.cloud

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.justplay.pos.core.interfaces.IRoomRepository
import com.justplay.pos.core.models.Reservation
import com.justplay.pos.core.models.Room
import com.justplay.pos.core.models.RoomOrder
import com.justplay.pos.core.models.RoomState
import com.justplay.pos.core.models.SessionRecord
import com.justplay.pos.infrastructure.security.DeviceSecurityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Single Responsibility Principle (SRP):
 * Administra la persistencia, consulta, sincronización y auditoría de salas en Firebase Realtime DB.
 * Protegido por AuthGuard para evitar accesos anónimos o desvinculados.
 */
class FirebaseRoomRepository(
    private var baseUrl: String = "https://justplay-a081e-default-rtdb.firebaseio.com",
    private val securityService: DeviceSecurityService? = null
) : IRoomRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val _roomsFlow = MutableStateFlow<List<Room>>(emptyList())
    override val roomsFlow: StateFlow<List<Room>> = _roomsFlow.asStateFlow()

    private val _reservationsFlow = MutableStateFlow<List<Reservation>>(emptyList())
    override val reservationsFlow: StateFlow<List<Reservation>> = _reservationsFlow.asStateFlow()

    private val _recordsFlow = MutableStateFlow<List<SessionRecord>>(emptyList())
    override val recordsFlow: StateFlow<List<SessionRecord>> = _recordsFlow.asStateFlow()

    fun updateBaseUrl(newUrl: String) {
        baseUrl = newUrl.removeSuffix("/")
    }

    override suspend fun fetchReservations(): List<Reservation> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/reservas.json")
                .header("X-App-Version", "1.0.0")
                .header("X-Device-Id", securityService?.identity?.deviceId ?: "POS-PC-UNKNOWN")
                .header("X-Session-Token", securityService?.identity?.sessionToken ?: "")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext _reservationsFlow.value

                val bodyStr = response.body?.string() ?: return@withContext _reservationsFlow.value
                if (bodyStr == "null" || bodyStr.isBlank()) {
                    _reservationsFlow.value = emptyList()
                    return@withContext emptyList()
                }

                val list = mutableListOf<Reservation>()
                val jsonElement = JsonParser.parseString(bodyStr)

                fun parseReservation(key: String, rObj: com.google.gson.JsonObject): Reservation {
                    val idFirebase = rObj.get("idFirebase")?.asString ?: key
                    val nombre = rObj.get("nombre")?.asString ?: ""
                    val celular = rObj.get("celular")?.asString ?: ""
                    val fecha = rObj.get("fecha")?.asString ?: ""
                    val horaProgramada = rObj.get("horaProgramada")?.asString ?: ""
                    val tipoSala = rObj.get("tipoSala")?.asString 
                        ?: rObj.get("tipo")?.asString 
                        ?: rObj.get("categoria")?.asString 
                        ?: ""
                    val adelanto = rObj.get("adelanto")?.asString ?: ""
                    val metodoPagoAdelanto = rObj.get("metodoPagoAdelanto")?.asString ?: "Efectivo"
                    val comprobanteUri = if (rObj.has("comprobanteUri") && !rObj.get("comprobanteUri").isJsonNull) rObj.get("comprobanteUri").asString else null
                    val duracion = rObj.get("duracion")?.asString ?: "1:00"
                    
                    val cantidadVolantes = try {
                        when {
                            rObj.has("cantidadVolantes") && !rObj.get("cantidadVolantes").isJsonNull -> {
                                val elem = rObj.get("cantidadVolantes")
                                if (elem.isJsonPrimitive && elem.asJsonPrimitive.isNumber) elem.asInt
                                else elem.asString.toIntOrNull() ?: 1
                            }
                            rObj.has("volantes") && !rObj.get("volantes").isJsonNull -> {
                                val elem = rObj.get("volantes")
                                if (elem.isJsonPrimitive && elem.asJsonPrimitive.isNumber) elem.asInt
                                else elem.asString.toIntOrNull() ?: 1
                            }
                            else -> 1
                        }
                    } catch (_: Exception) { 1 }

                    val asistio = rObj.get("asistio")?.asBoolean ?: false
                    val horaLlegada = if (rObj.has("horaLlegada") && !rObj.get("horaLlegada").isJsonNull) rObj.get("horaLlegada").asString else null

                    return Reservation(
                        idFirebase = idFirebase,
                        nombre = nombre,
                        celular = celular,
                        fecha = fecha,
                        horaProgramada = horaProgramada,
                        tipoSala = tipoSala,
                        adelanto = adelanto,
                        metodoPagoAdelanto = metodoPagoAdelanto,
                        comprobanteUri = comprobanteUri,
                        duracion = duracion,
                        cantidadVolantes = cantidadVolantes,
                        asistio = asistio,
                        horaLlegada = horaLlegada
                    )
                }

                if (jsonElement.isJsonObject) {
                    val obj = jsonElement.asJsonObject
                    for ((key, value) in obj.entrySet()) {
                        if (!value.isJsonObject) continue
                        list.add(parseReservation(key, value.asJsonObject))
                    }
                } else if (jsonElement.isJsonArray) {
                    val arr = jsonElement.asJsonArray
                    for (i in 0 until arr.size()) {
                        val item = arr.get(i)
                        if (!item.isJsonObject) continue
                        list.add(parseReservation(i.toString(), item.asJsonObject))
                    }
                }

                _reservationsFlow.value = list
                list
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _reservationsFlow.value
        }
    }

    override suspend fun fetchRecords(): List<SessionRecord> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/registros.json")
                .header("X-App-Version", "1.0.0")
                .header("X-Device-Id", securityService?.identity?.deviceId ?: "POS-PC-UNKNOWN")
                .header("X-Session-Token", securityService?.identity?.sessionToken ?: "")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext _recordsFlow.value

                val bodyStr = response.body?.string() ?: return@withContext _recordsFlow.value
                if (bodyStr == "null" || bodyStr.isBlank()) {
                    _recordsFlow.value = emptyList()
                    return@withContext emptyList()
                }

                val list = mutableListOf<SessionRecord>()
                val jsonElement = JsonParser.parseString(bodyStr)

                fun parseRecord(key: String, rObj: com.google.gson.JsonObject): SessionRecord? {
                    val eliminado = rObj.get("eliminado")?.asBoolean ?: false
                    if (eliminado) return null // Ignorar registros eliminados por el usuario

                    val tipo = rObj.get("tipo")?.asString ?: ""
                    val nombreElemento = rObj.get("nombreElemento")?.asString ?: ""
                    // Excluir ventas puras de mostrador y ventas directas de productos del tablero de reservas/salas
                    val isVentaPura = tipo.contains("Venta Directa", ignoreCase = true) ||
                                     tipo.contains("Venta", ignoreCase = true) ||
                                     nombreElemento.contains("Mostrador", ignoreCase = true) ||
                                     nombreElemento.contains("Venta Directa", ignoreCase = true) ||
                                     nombreElemento.contains("Snack", ignoreCase = true)
                    if (isVentaPura && !nombreElemento.contains("Sala", ignoreCase = true) && !nombreElemento.contains("Cine", ignoreCase = true)) {
                        return null
                    }
                    val idFirebase = rObj.get("idFirebase")?.asString ?: key
                    val cliente = rObj.get("cliente")?.asString ?: ""
                    val celular = if (rObj.has("celular") && !rObj.get("celular").isJsonNull) rObj.get("celular").asString else null
                    val peliculaJuego = if (rObj.has("peliculaJuego") && !rObj.get("peliculaJuego").isJsonNull) rObj.get("peliculaJuego").asString else null
                    val tiempoConfigurado = if (rObj.has("tiempoConfigurado") && !rObj.get("tiempoConfigurado").isJsonNull) rObj.get("tiempoConfigurado").asString else null
                    val fechaHora = rObj.get("fechaHora")?.asString ?: ""
                    val timestamp = rObj.get("timestamp")?.asLong ?: 0L
                    val pedidos = rObj.get("pedidos")?.asString ?: ""
                    val totalCobrado = rObj.get("totalCobrado")?.asDouble ?: 0.0
                    val metodoPago = rObj.get("metodoPago")?.asString ?: "Efectivo"
                    val comprobanteUri = if (rObj.has("comprobanteUri") && !rObj.get("comprobanteUri").isJsonNull) rObj.get("comprobanteUri").asString else null
                    val cantidadVolantes = if (rObj.has("cantidadVolantes") && !rObj.get("cantidadVolantes").isJsonNull) rObj.get("cantidadVolantes").asInt else null

                    return SessionRecord(
                        idFirebase = idFirebase,
                        tipo = tipo,
                        nombreElemento = nombreElemento,
                        cliente = cliente,
                        celular = celular,
                        peliculaJuego = peliculaJuego,
                        tiempoConfigurado = tiempoConfigurado,
                        fechaHora = fechaHora,
                        timestamp = timestamp,
                        pedidos = pedidos,
                        totalCobrado = totalCobrado,
                        metodoPago = metodoPago,
                        comprobanteUri = comprobanteUri,
                        cantidadVolantes = cantidadVolantes,
                        eliminado = false
                    )
                }

                if (jsonElement.isJsonObject) {
                    val obj = jsonElement.asJsonObject
                    for ((key, value) in obj.entrySet()) {
                        if (!value.isJsonObject) continue
                        val rec = parseRecord(key, value.asJsonObject)
                        if (rec != null) list.add(rec)
                    }
                } else if (jsonElement.isJsonArray) {
                    val arr = jsonElement.asJsonArray
                    for (i in 0 until arr.size()) {
                        val item = arr.get(i)
                        if (!item.isJsonObject) continue
                        val rec = parseRecord(i.toString(), item.asJsonObject)
                        if (rec != null) list.add(rec)
                    }
                }

                _recordsFlow.value = list
                list
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _recordsFlow.value
        }
    }

    override suspend fun fetchRooms(): List<Room> = withContext(Dispatchers.IO) {
        try {
            // AuthGuard: Validar versión mínima (426) y autorización de terminal (401)
            securityService?.validateAuthGuard("/salas")

            val request = Request.Builder()
                .url("$baseUrl/salas.json")
                .header("X-App-Version", "1.0.0")
                .header("X-Device-Id", securityService?.identity?.deviceId ?: "POS-PC-UNKNOWN")
                .header("X-Session-Token", securityService?.identity?.sessionToken ?: "")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext _roomsFlow.value

                val bodyStr = response.body?.string() ?: return@withContext _roomsFlow.value
                if (bodyStr == "null" || bodyStr.isEmpty()) {
                    _roomsFlow.value = emptyList()
                    return@withContext emptyList()
                }

                val roomsList = mutableListOf<Room>()
                val mapType = object : TypeToken<Map<String, Any?>>() {}.type
                val rawMap: Map<String, Any?> = gson.fromJson(bodyStr, mapType) ?: emptyMap()

                for ((key, value) in rawMap) {
                    if (value !is Map<*, *>) continue
                    val idVal = (value["id"] as? Number)?.toInt() ?: key.toIntOrNull() ?: 0
                    val idFirebase = value["idFirebase"] as? String ?: key
                    val nombre = value["nombre"] as? String ?: "Sala $idVal"
                    val estadoStr = value["estado"] as? String ?: "DISPONIBLE"
                    val estado = try {
                        RoomState.valueOf(estadoStr.uppercase())
                    } catch (_: Exception) {
                        RoomState.DISPONIBLE
                    }
                    val horaReserva = value["horaReserva"] as? String
                    val cliente = value["cliente"] as? String
                    val celular = value["celular"] as? String
                    val pelicula = value["pelicula"] as? String
                    val tiempoRestanteMillis = (value["tiempoRestanteMillis"] as? Number)?.toLong()
                    val horaFinProgramada = value["horaFinProgramada"] as? String
                    val endTimeMillis = (value["endTimeMillis"] as? Number)?.toLong()
                    val precioCobrado = (value["precioCobrado"] as? Number)?.toDouble() ?: 0.0
                    val metodoPago = value["metodoPago"] as? String ?: "Efectivo"
                    val comprobanteUri = value["comprobanteUri"] as? String

                    // Parsear pedidos
                    val pedidosList = mutableListOf<RoomOrder>()
                    val rawPedidos = value["pedidos"]
                    if (rawPedidos is List<*>) {
                        for (p in rawPedidos) {
                            if (p is Map<*, *>) {
                                val pId = p["idFirebase"] as? String ?: ""
                                val cant = (p["cantidad"] as? Number)?.toInt() ?: 1
                                val nom = p["nombre"] as? String ?: ""
                                val pre = (p["precioUnidad"] as? Number)?.toDouble() ?: 0.0
                                val ent = p["estaEntregado"] as? Boolean ?: true
                                pedidosList.add(RoomOrder(pId, cant, nom, pre, ent))
                            }
                        }
                    } else if (rawPedidos is Map<*, *>) {
                        for ((_, p) in rawPedidos) {
                            if (p is Map<*, *>) {
                                val pId = p["idFirebase"] as? String ?: ""
                                val cant = (p["cantidad"] as? Number)?.toInt() ?: 1
                                val nom = p["nombre"] as? String ?: ""
                                val pre = (p["precioUnidad"] as? Number)?.toDouble() ?: 0.0
                                val ent = p["estaEntregado"] as? Boolean ?: true
                                pedidosList.add(RoomOrder(pId, cant, nom, pre, ent))
                            }
                        }
                    }

                    val seccionId = value["seccionId"] as? String ?: ""
                    val seccionNombre = value["seccionNombre"] as? String ?: ""

                    roomsList.add(
                        Room(
                            id = idVal,
                            idFirebase = idFirebase,
                            nombre = nombre,
                            estado = estado,
                            horaReserva = horaReserva,
                            cliente = cliente,
                            celular = celular,
                            pelicula = pelicula,
                            tiempoRestanteMillis = tiempoRestanteMillis,
                            horaFinProgramada = horaFinProgramada,
                            endTimeMillis = endTimeMillis,
                            precioCobrado = precioCobrado,
                            metodoPago = metodoPago,
                            comprobanteUri = comprobanteUri,
                            pedidos = pedidosList,
                            seccionId = seccionId,
                            seccionNombre = seccionNombre
                        )
                    )
                }

                roomsList.sortBy { it.id }
                _roomsFlow.value = roomsList
                roomsList
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _roomsFlow.value
        }
    }

    override suspend fun updateRoom(room: Room): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf(
                "id" to room.id,
                "idFirebase" to room.idFirebase,
                "nombre" to room.nombre,
                "estado" to room.estado.name,
                "horaReserva" to room.horaReserva,
                "cliente" to room.cliente,
                "celular" to room.celular,
                "pelicula" to room.pelicula,
                "tiempoRestanteMillis" to room.tiempoRestanteMillis,
                "horaFinProgramada" to room.horaFinProgramada,
                "endTimeMillis" to room.endTimeMillis,
                "precioCobrado" to room.precioCobrado,
                "metodoPago" to room.metodoPago,
                "comprobanteUri" to room.comprobanteUri,
                "pedidos" to room.pedidos,
                "seccionId" to room.seccionId,
                "seccionNombre" to room.seccionNombre
            )
            val jsonBody = gson.toJson(payload).toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$baseUrl/salas/${room.id}.json")
                .put(jsonBody)
                .build()

            val ok = client.newCall(request).execute().use { it.isSuccessful }
            if (ok) {
                val current = _roomsFlow.value.toMutableList()
                val idx = current.indexOfFirst { it.id == room.id }
                if (idx >= 0) {
                    current[idx] = room
                    _roomsFlow.value = current
                }
            }
            ok
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun startRoom(
        roomId: Int,
        clientName: String,
        clientPhone: String,
        gameOrMovie: String,
        durationMinutes: Int,
        price: Double,
        paymentMethod: String,
        pedidos: List<RoomOrder>,
        receiptImageUrl: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentRooms = fetchRooms()
            val existing = currentRooms.find { it.id == roomId } ?: return@withContext false

            val durMillis = if (durationMinutes > 0) durationMinutes * 60 * 1000L else null
            val endTime = if (durMillis != null) System.currentTimeMillis() + durMillis else null
            val horaFin = if (endTime != null) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = endTime
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
            } else "Libre"

            val updatedRoom = existing.copy(
                estado = RoomState.OCUPADA,
                cliente = clientName,
                celular = clientPhone,
                pelicula = gameOrMovie,
                tiempoRestanteMillis = durMillis,
                horaFinProgramada = horaFin,
                endTimeMillis = endTime,
                precioCobrado = price,
                metodoPago = paymentMethod,
                comprobanteUri = receiptImageUrl,
                pedidos = pedidos
            )

            val ok = updateRoom(updatedRoom)
            if (ok) {
                val fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val regId = "REG_${System.currentTimeMillis()}"

                // 1. Registrar el Alquiler de Sala por separado (ej. 35 Bs)
                val regPayload = mapOf(
                    "idFirebase" to regId,
                    "tipo" to existing.categoria,
                    "nombreElemento" to existing.nombre,
                    "cliente" to clientName,
                    "celular" to clientPhone,
                    "peliculaJuego" to gameOrMovie,
                    "tiempoConfigurado" to if (durationMinutes > 0) "$durationMinutes min" else "Libre",
                    "fechaHora" to fechaHora,
                    "pedidos" to "Alquiler de Sala",
                    "totalCobrado" to price, // Únicamente el precio del alquiler
                    "metodoPago" to paymentMethod,
                    "comprobanteUri" to receiptImageUrl,
                    "creadoPor" to "POS Desktop (PC)",
                    "eliminado" to false
                )
                val jsonReg = gson.toJson(regPayload).toRequestBody(jsonMediaType)
                val regRequest = Request.Builder()
                    .url("$baseUrl/registros/$regId.json")
                    .put(jsonReg)
                    .build()
                client.newCall(regRequest).execute().close()

                val regAuditRequest = Request.Builder()
                    .url("$baseUrl/auditoria_registros/$regId.json")
                    .put(jsonReg)
                    .build()
                client.newCall(regAuditRequest).execute().close()

                // 2. Registrar cada consumo/producto agregado por separado (ej. Nacho nax 3 Bs)
                for (pedido in pedidos) {
                    val pKey = if (pedido.idFirebase.isNotBlank()) pedido.idFirebase else "ORD_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
                    val pPayload = mapOf(
                        "idFirebase" to pKey,
                        "tipo" to "Consumo",
                        "nombreElemento" to "Consumo ${existing.nombre}",
                        "cliente" to clientName,
                        "celular" to clientPhone,
                        "fechaHora" to fechaHora,
                        "pedidos" to "${pedido.cantidad}x ${pedido.nombre} (${pedido.subtotal} Bs)",
                        "totalCobrado" to pedido.subtotal,
                        "metodoPago" to paymentMethod,
                        "creadoPor" to "POS Desktop (PC)",
                        "eliminado" to false
                    )
                    val jsonP = gson.toJson(pPayload).toRequestBody(jsonMediaType)
                    val pReq = Request.Builder()
                        .url("$baseUrl/registros/$pKey.json")
                        .put(jsonP)
                        .build()
                    client.newCall(pReq).execute().close()

                    val pAuditReq = Request.Builder()
                        .url("$baseUrl/auditoria_registros/$pKey.json")
                        .put(jsonP)
                        .build()
                    client.newCall(pAuditReq).execute().close()
                }

                // Auditoría de salas
                logRoomAudit(
                    actionType = "INICIO_TIEMPO",
                    roomId = roomId.toString(),
                    roomName = existing.nombre,
                    category = existing.categoria,
                    durationMinutes = durationMinutes,
                    details = "Iniciado en PC Desktop para $clientName ($price Bs sala, ${pedidos.size} consumos)"
                )
                fetchRooms()
            }
            ok
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun addTimeToRoom(roomId: Int, extraMinutes: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentRooms = fetchRooms()
            val existing = currentRooms.find { it.id == roomId } ?: return@withContext false

            val currentEnd = existing.endTimeMillis ?: System.currentTimeMillis()
            val newEnd = currentEnd + (extraMinutes * 60 * 1000L)
            val cal = Calendar.getInstance()
            cal.timeInMillis = newEnd
            val nuevaHoraFin = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)

            val updatedRoom = existing.copy(
                endTimeMillis = newEnd,
                horaFinProgramada = nuevaHoraFin
            )

            val ok = updateRoom(updatedRoom)
            if (ok) {
                logRoomAudit(
                    actionType = "AGREGAR_TIEMPO",
                    roomId = roomId.toString(),
                    roomName = existing.nombre,
                    category = existing.categoria,
                    durationMinutes = extraMinutes,
                    details = "Se aumentaron $extraMinutes min desde PC Desktop. Nueva hora fin: $nuevaHoraFin"
                )
                fetchRooms()
            }
            ok
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun clearRoom(roomId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentRooms = fetchRooms()
            val existing = currentRooms.find { it.id == roomId } ?: return@withContext false

            val clearedRoom = existing.copy(
                estado = RoomState.DISPONIBLE,
                cliente = null,
                celular = null,
                pelicula = null,
                tiempoRestanteMillis = null,
                horaFinProgramada = null,
                endTimeMillis = null,
                precioCobrado = 0.0,
                metodoPago = "Efectivo",
                comprobanteUri = null,
                pedidos = emptyList()
            )

            val ok = updateRoom(clearedRoom)
            if (ok) {
                logRoomAudit(
                    actionType = "LIBERAR_SALA",
                    roomId = roomId.toString(),
                    roomName = existing.nombre,
                    category = existing.categoria,
                    details = "Sala finalizada y liberada desde PC Desktop"
                )
                fetchRooms()
            }
            ok
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun addOrderToRoom(roomId: Int, order: RoomOrder): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentRooms = fetchRooms()
            val existing = currentRooms.find { it.id == roomId } ?: return@withContext false

            val updatedOrders = existing.pedidos.toMutableList()
            updatedOrders.add(order)

            val updatedRoom = existing.copy(pedidos = updatedOrders)
            val ok = updateRoom(updatedRoom)
            if (ok) {
                // Registrar el consumo/producto agregado APARTE en /registros
                val fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val regId = if (order.idFirebase.isNotBlank()) order.idFirebase else "ORD_${System.currentTimeMillis()}"
                val regPayload = mapOf(
                    "idFirebase" to regId,
                    "tipo" to "Consumo",
                    "nombreElemento" to "Consumo ${existing.nombre}",
                    "cliente" to (existing.cliente ?: "Cliente"),
                    "celular" to existing.celular,
                    "fechaHora" to fechaHora,
                    "pedidos" to "${order.cantidad}x ${order.nombre} (${order.subtotal} Bs)",
                    "totalCobrado" to order.subtotal,
                    "metodoPago" to existing.metodoPago,
                    "creadoPor" to "POS Desktop (PC)",
                    "eliminado" to false
                )
                val jsonReg = gson.toJson(regPayload).toRequestBody(jsonMediaType)
                val regRequest = Request.Builder()
                    .url("$baseUrl/registros/$regId.json")
                    .put(jsonReg)
                    .build()
                client.newCall(regRequest).execute().close()

                val regAuditRequest = Request.Builder()
                    .url("$baseUrl/auditoria_registros/$regId.json")
                    .put(jsonReg)
                    .build()
                client.newCall(regAuditRequest).execute().close()

                fetchRooms()
            }
            ok
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun removeOrderFromRoom(roomId: Int, orderIndex: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentRooms = fetchRooms()
            val existing = currentRooms.find { it.id == roomId } ?: return@withContext false

            if (orderIndex in existing.pedidos.indices) {
                val order = existing.pedidos[orderIndex]
                val updatedOrders = existing.pedidos.toMutableList()
                updatedOrders.removeAt(orderIndex)
                val updatedRoom = existing.copy(pedidos = updatedOrders)
                val ok = updateRoom(updatedRoom)
                if (ok) {
                    if (order.idFirebase.isNotBlank()) {
                        val deleteRequest = Request.Builder()
                            .url("$baseUrl/registros/${order.idFirebase}.json")
                            .delete()
                            .build()
                        client.newCall(deleteRequest).execute().close()
                    }
                    fetchRooms()
                }
                return@withContext ok
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun updateRoomRecordSession(room: Room): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Obtener registros existentes para encontrar la sesión activa de esta sala
            val allRecords = fetchRecords()
            val targetRecord = allRecords.lastOrNull {
                it.nombreElemento == room.nombre && (it.pedidos == "Alquiler de Sala" || it.tipo in listOf("Minicine", "Autitos", "Just Dance") || (room.seccionNombre.isNotBlank() && it.tipo == room.seccionNombre))
            }

            val regId = targetRecord?.idFirebase
            if (regId.isNullOrBlank()) {
                return@withContext false
            }

            val updates = mapOf<String, Any?>(
                "cliente" to (room.cliente ?: targetRecord.cliente),
                "celular" to (room.celular ?: targetRecord.celular),
                "peliculaJuego" to (room.pelicula ?: targetRecord.peliculaJuego),
                "totalCobrado" to room.precioCobrado,
                "metodoPago" to room.metodoPago,
                "comprobanteUri" to (room.comprobanteUri ?: targetRecord.comprobanteUri)
            )

            val jsonBody = gson.toJson(updates).toRequestBody(jsonMediaType)

            val regRequest = Request.Builder()
                .url("$baseUrl/registros/$regId.json")
                .patch(jsonBody)
                .build()
            client.newCall(regRequest).execute().close()

            val regAuditRequest = Request.Builder()
                .url("$baseUrl/auditoria_registros/$regId.json")
                .patch(jsonBody)
                .build()
            client.newCall(regAuditRequest).execute().close()

            // Refrescar registros locales
            fetchRecords()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun logRoomAudit(
        actionType: String,
        roomId: String,
        roomName: String,
        category: String,
        durationMinutes: Int,
        details: String
    ) {
        try {
            val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            val logId = "AUD_ROOM_${System.currentTimeMillis()}"
            val payload = mapOf(
                "id" to logId,
                "tipo_accion" to actionType,
                "sala_id" to roomId,
                "sala_nombre" to roomName,
                "categoria" to category,
                "tiempo_minutos" to durationMinutes,
                "usuario_nombre" to "Cajero Desktop",
                "usuario_rol" to "Empleado",
                "dispositivo_modelo" to "PC Windows Desktop (POS)",
                "dispositivo_id" to "WIN_POS_01",
                "fecha_hora" to now,
                "timestamp" to System.currentTimeMillis(),
                "detalles" to details
            )
            val jsonBody = gson.toJson(payload).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/auditoria_salas/$logId.json")
                .put(jsonBody)
                .build()
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

package com.justplay.pos.infrastructure.cloud

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.justplay.pos.core.interfaces.ICloudSyncService
import com.justplay.pos.core.interfaces.ILocalStorage
import com.justplay.pos.core.models.PaymentMethod
import com.justplay.pos.core.models.Product
import com.justplay.pos.core.models.SaleTransaction
import com.justplay.pos.core.models.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.justplay.pos.infrastructure.security.DeviceSecurityService
import java.util.concurrent.TimeUnit

class FirebaseSyncService(
    private var baseUrl: String = "https://justplay-a081e-default-rtdb.firebaseio.com",
    private val securityService: DeviceSecurityService? = null
) : ICloudSyncService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val _syncStatusFlow = MutableStateFlow(SyncStatus.ONLINE_SYNCED)
    override val syncStatusFlow: StateFlow<SyncStatus> = _syncStatusFlow.asStateFlow()

    fun updateBaseUrl(newUrl: String) {
        baseUrl = newUrl.removeSuffix("/")
    }

    override suspend fun isPosAuthorized(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Verificar si el dispositivo fue revocado o si el servicio global está deshabilitado
            if (securityService != null && (!securityService.identity.isAuthorized || securityService.isDeviceRevokedFlow.value)) {
                return@withContext false
            }

            val request = Request.Builder()
                .url("$baseUrl/config_pos/habilitado.json")
                .header("X-App-Version", "1.0.0")
                .header("X-Device-Id", securityService?.identity?.deviceId ?: "POS-PC-UNKNOWN")
                .header("X-Session-Token", securityService?.identity?.sessionToken ?: "")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()?.trim()
                    if (body == "false") {
                        return@withContext false
                    }
                }
                true
            }
        } catch (_: Exception) {
            true // En caso de fallo de red en modo offline, permitir ventas locales
        }
    }

    override suspend fun syncSale(sale: SaleTransaction): Boolean = withContext(Dispatchers.IO) {
        try {
            securityService?.validateAuthGuard("/registros")

            if (!isPosAuthorized()) {
                _syncStatusFlow.value = SyncStatus.ERROR
                return@withContext false
            }
            _syncStatusFlow.value = SyncStatus.SYNCING

            val esSala = sale.destinationType == "SALA" && sale.targetRoomName != null
            val tipoRegistro = if (esSala) "Consumo" else "Venta Directa"
            val nombreElem = if (esSala) "Consumo ${sale.targetRoomName}" else "Venta Mostrador POS"

            val metodoPagoStr = when (sale.paymentMethod) {
                PaymentMethod.EFECTIVO -> "Efectivo"
                PaymentMethod.QR -> "QR"
                PaymentMethod.MIXTO -> "Mixto (💵 ${sale.cashReceived} Bs | 📱 ${sale.qrAmount} Bs)"
            }

            val actualCash = when (sale.paymentMethod) {
                PaymentMethod.EFECTIVO -> sale.totalAmount
                PaymentMethod.QR -> 0.0
                PaymentMethod.MIXTO -> sale.cashReceived
            }
            val actualQR = when (sale.paymentMethod) {
                PaymentMethod.EFECTIVO -> 0.0
                PaymentMethod.QR -> sale.totalAmount
                PaymentMethod.MIXTO -> sale.qrAmount
            }

            // 1. Estructurar el objeto exactamente compatible con RegistroEntry de JustPlay / AdminJustPlay
            val payload = mapOf(
                "idFirebase" to sale.id,
                "tipo" to tipoRegistro,
                "nombreElemento" to nombreElem,
                "cliente" to sale.clientName,
                "celular" to (sale.clientPhone ?: "S/N"),
                "fechaHora" to sale.dateTime,
                "pedidos" to sale.formattedSummary,
                "totalCobrado" to sale.totalAmount,
                "metodoPago" to metodoPagoStr,
                "montoEfectivo" to actualCash,
                "montoQR" to actualQR,
                "creadoPor" to sale.operatorName,
                "comprobanteUri" to sale.receiptImageUrl,
                "eliminado" to false,
                "cantidadVolantes" to 0
            )
            val jsonBody = gson.toJson(payload).toRequestBody(jsonMediaType)

            // 2. Enviar a /registros/{saleId}.json
            val requestRegistros = Request.Builder()
                .url("$baseUrl/registros/${sale.id}.json")
                .put(jsonBody)
                .build()

            val resp1 = client.newCall(requestRegistros).execute()
            val ok1 = resp1.isSuccessful
            resp1.close()

            // 3. Enviar a /auditoria_registros/{saleId}.json (Registro inmutable del Admin)
            val requestAuditoria = Request.Builder()
                .url("$baseUrl/auditoria_registros/${sale.id}.json")
                .put(jsonBody)
                .build()

            val resp2 = client.newCall(requestAuditoria).execute()
            val ok2 = resp2.isSuccessful
            resp2.close()

            // 4. Decrementar stock de los productos vendidos en Firebase
            for (item in sale.items) {
                decrementStockInCloud(item.product.barcode, item.quantity)
            }

            val success = ok1 && ok2
            _syncStatusFlow.value = if (success) SyncStatus.ONLINE_SYNCED else SyncStatus.OFFLINE_SAVED
            success
        } catch (e: Exception) {
            e.printStackTrace()
            _syncStatusFlow.value = SyncStatus.OFFLINE_SAVED
            false
        }
    }

    override suspend fun emitQrValidationRequest(
        saleId: String,
        tipo: String,
        salaId: Int?,
        salaNombre: String,
        cliente: String,
        montoTotal: Double,
        montoEfectivo: Double,
        montoQR: Double,
        metodoPago: String
    ): String = withContext(Dispatchers.IO) {
        val solicitudId = "SOL_QR_${System.currentTimeMillis()}"
        val payload = mapOf(
            "id" to solicitudId,
            "saleId" to saleId,
            "tipo" to tipo,
            "salaId" to salaId,
            "salaNombre" to salaNombre,
            "cliente" to cliente,
            "montoTotal" to montoTotal,
            "montoEfectivo" to montoEfectivo,
            "montoQR" to montoQR,
            "metodoPago" to metodoPago,
            "estado" to "PENDIENTE",
            "timestamp" to System.currentTimeMillis()
        )
        val jsonBody = gson.toJson(payload).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/solicitudes_pago_qr/$solicitudId.json")
            .put(jsonBody)
            .build()
        client.newCall(request).execute().close()
        solicitudId
    }

    override suspend fun checkQrValidationStatus(solicitudId: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/solicitudes_pago_qr/$solicitudId.json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                if (bodyStr == "null" || bodyStr.isEmpty()) return@withContext null
                val mapType = object : TypeToken<Map<String, Any?>>() {}.type
                gson.fromJson<Map<String, Any?>>(bodyStr, mapType)
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun cancelQrValidationRequest(solicitudId: String) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/solicitudes_pago_qr/$solicitudId.json")
                .delete()
                .build()
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun fetchCatalogFromCloud(): List<Product>? = withContext(Dispatchers.IO) {
        try {
            securityService?.validateAuthGuard("/productos_catalogo")

            val request = Request.Builder()
                .url("$baseUrl/productos_catalogo.json")
                .header("X-App-Version", "1.0.0")
                .header("X-Device-Id", securityService?.identity?.deviceId ?: "POS-PC-UNKNOWN")
                .header("X-Session-Token", securityService?.identity?.sessionToken ?: "")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }

                val bodyStr = response.body?.string() ?: return@withContext null
                if (bodyStr == "null" || bodyStr.isEmpty()) {
                    return@withContext emptyList()
                }

                val mapType = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
                val map: Map<String, Map<String, Any>> = gson.fromJson(bodyStr, mapType) ?: emptyMap()

                val products = mutableListOf<Product>()
                for ((barcode, data) in map) {
                    val name = data["nombre"] as? String ?: data["name"] as? String ?: "Producto $barcode"
                    val category = data["categoria"] as? String ?: data["category"] as? String ?: "General"
                    val unitPrice = (data["precio_unitario"] as? Number)?.toDouble()
                        ?: (data["unitPrice"] as? Number)?.toDouble() ?: 0.0
                    val costPrice = (data["costo_compra"] as? Number)?.toDouble()
                        ?: (data["costPrice"] as? Number)?.toDouble() ?: 0.0
                    val stock = (data["stock_actual"] as? Number)?.toInt()
                        ?: (data["currentStock"] as? Number)?.toInt() ?: 0
                    val minStock = (data["stock_minimo_alerta"] as? Number)?.toInt()
                        ?: (data["minStockAlert"] as? Number)?.toInt() ?: 5
                    val active = data["activo"] as? Boolean ?: data["active"] as? Boolean ?: true
                    val unitOfMeasure = data["unidad_medida"] as? String ?: data["unitOfMeasure"] as? String ?: "Unidad"
                    val lastUpdated = data["fecha_actualizacion"] as? String ?: ""

                    products.add(
                        Product(
                            barcode = barcode,
                            name = name,
                            category = category,
                            unitPrice = unitPrice,
                            costPrice = costPrice,
                            currentStock = stock,
                            minStockAlert = minStock,
                            active = active,
                            unitOfMeasure = unitOfMeasure,
                            lastUpdated = lastUpdated
                        )
                    )
                }
                products
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun pushProductToCloud(product: Product): Boolean = withContext(Dispatchers.IO) {
        try {
            securityService?.validateAuthGuard("/productos_catalogo")

            val payload = mapOf(
                "codigo_barras" to product.barcode,
                "nombre" to product.name,
                "categoria" to product.category,
                "precio_unitario" to product.unitPrice,
                "costo_compra" to product.costPrice,
                "stock_actual" to product.currentStock,
                "stock_minimo_alerta" to product.minStockAlert,
                "activo" to product.active,
                "unidad_medida" to product.unitOfMeasure,
                "fecha_actualizacion" to product.lastUpdated
            )
            val jsonBody = gson.toJson(payload).toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$baseUrl/productos_catalogo/${product.barcode}.json")
                .put(jsonBody)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun decrementStockInCloud(barcode: String, quantitySold: Int) {
        try {
            // Obtener stock actual
            val getRequest = Request.Builder()
                .url("$baseUrl/productos_catalogo/$barcode/stock_actual.json")
                .get()
                .build()

            var currentStock = 0
            client.newCall(getRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    currentStock = body?.toIntOrNull() ?: 0
                }
            }

            val newStock = maxOf(0, currentStock - quantitySold)
            val updateRequest = Request.Builder()
                .url("$baseUrl/productos_catalogo/$barcode/stock_actual.json")
                .put(newStock.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(updateRequest).execute().close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun syncPendingOfflineSales(localStorage: ILocalStorage): Int = withContext(Dispatchers.IO) {
        var syncedCount = 0
        try {
            val pendingSales = localStorage.getUnsyncedSales()
            for (sale in pendingSales) {
                val ok = syncSale(sale)
                if (ok) {
                    localStorage.markSaleAsSynced(sale.id)
                    syncedCount++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        syncedCount
    }
}

package com.justplay.pos.core.interfaces

import com.justplay.pos.core.models.Product
import com.justplay.pos.core.models.SaleTransaction
import com.justplay.pos.core.models.SyncStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface para sincronización remota con Firebase Realtime Database.
 */
interface ICloudSyncService {
    val syncStatusFlow: StateFlow<SyncStatus>
    suspend fun isPosAuthorized(): Boolean
    suspend fun syncSale(sale: SaleTransaction): Boolean
    suspend fun fetchCatalogFromCloud(): List<Product>?
    suspend fun pushProductToCloud(product: Product): Boolean
    suspend fun deleteProductFromCloud(barcode: String): Boolean
    suspend fun fetchBasePrices(): Map<String, Double>
    suspend fun syncPendingOfflineSales(localStorage: ILocalStorage): Int
    suspend fun emitQrValidationRequest(
        saleId: String,
        tipo: String,
        salaId: Int?,
        salaNombre: String,
        cliente: String,
        montoTotal: Double,
        montoEfectivo: Double,
        montoQR: Double,
        metodoPago: String
    ): String
    suspend fun checkQrValidationStatus(solicitudId: String): Map<String, Any?>?
    suspend fun cancelQrValidationRequest(solicitudId: String)
}

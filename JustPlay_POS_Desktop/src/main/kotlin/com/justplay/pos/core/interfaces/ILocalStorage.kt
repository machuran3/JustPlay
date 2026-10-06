package com.justplay.pos.core.interfaces

import com.justplay.pos.core.models.Product
import com.justplay.pos.core.models.SaleTransaction

/**
 * Liskov Substitution: Abstracción de persistencia local en disco para modo offline y auditoría.
 */
interface ILocalStorage {
    suspend fun initialize()
    suspend fun saveSale(sale: SaleTransaction)
    suspend fun getUnsyncedSales(): List<SaleTransaction>
    suspend fun markSaleAsSynced(saleId: String)
    suspend fun getAllSales(limit: Int = 100): List<SaleTransaction>
    suspend fun cacheProducts(products: List<Product>)
    suspend fun getCachedProducts(): List<Product>
}

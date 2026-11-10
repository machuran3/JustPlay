package com.justplay.pos.services

import com.justplay.pos.core.interfaces.ICloudSyncService
import com.justplay.pos.core.interfaces.ILocalStorage
import com.justplay.pos.core.interfaces.IProductRepository
import com.justplay.pos.core.models.Product
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProductCatalogService(
    private val localStorage: ILocalStorage,
    private val cloudSyncService: ICloudSyncService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : IProductRepository {

    private val _productsFlow = MutableStateFlow<List<Product>>(emptyList())
    val productsFlow: StateFlow<List<Product>> = _productsFlow.asStateFlow()

    suspend fun initialize() {
        localStorage.initialize()
        loadLocalProducts()
        
        // Si no hay productos locales, sembramos un catálogo inicial de ejemplo
        if (_productsFlow.value.isEmpty()) {
            seedInitialDemoProducts()
        }

        // Intentar sincronizar catálogo con Firebase en segundo plano
        scope.launch {
            syncWithCloud()
        }
    }

    private suspend fun loadLocalProducts() {
        val cached = localStorage.getCachedProducts()
        _productsFlow.value = cached
    }

    suspend fun syncWithCloud() {
        val cloudProducts = cloudSyncService.fetchCatalogFromCloud()
        if (!cloudProducts.isNullOrEmpty()) {
            localStorage.cacheProducts(cloudProducts)
            _productsFlow.value = cloudProducts
        }
    }

    override suspend fun getProductByBarcode(barcode: String): Product? {
        val clean = barcode.trim()
        return _productsFlow.value.find { it.barcode.equals(clean, ignoreCase = true) }
    }

    override suspend fun getAllProducts(): List<Product> {
        return _productsFlow.value
    }

    override suspend fun searchProducts(query: String): List<Product> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return _productsFlow.value
        return _productsFlow.value.filter {
            it.name.lowercase().contains(clean) ||
            it.barcode.contains(clean) ||
            it.category.lowercase().contains(clean)
        }
    }

    fun saveOrUpdateProduct(product: Product, oldBarcode: String? = null) {
        // Enforce business invariant: costPrice cannot be greater than or equal to unitPrice
        require(product.costPrice < product.unitPrice) {
            "El costo de compra (${product.costPrice}) no puede ser mayor o igual al precio de venta (${product.unitPrice})."
        }

        val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val updated = product.copy(lastUpdated = now)

        val currentList = _productsFlow.value.toMutableList()

        if (oldBarcode != null && oldBarcode.isNotBlank() && oldBarcode != updated.barcode) {
            currentList.removeAll { it.barcode == oldBarcode }
            scope.launch {
                localStorage.queuePendingCatalogSync(oldBarcode, "DELETE", null)
                val ok = cloudSyncService.deleteProductFromCloud(oldBarcode)
                if (ok) {
                    localStorage.removePendingCatalogSync(oldBarcode)
                }
            }
        }

        val index = currentList.indexOfFirst { it.barcode == updated.barcode }
        if (index >= 0) {
            currentList[index] = updated
        } else {
            currentList.add(updated)
        }

        _productsFlow.value = currentList
        scope.launch {
            localStorage.cacheProducts(listOf(updated))
            localStorage.queuePendingCatalogSync(updated.barcode, "UPSERT", updated)
            val ok = cloudSyncService.pushProductToCloud(updated)
            if (ok) {
                localStorage.removePendingCatalogSync(updated.barcode)
            }
        }
    }

    suspend fun syncPendingCatalogWithCloud() {
        try {
            val pending = localStorage.getPendingCatalogSync()
            val gson = com.google.gson.Gson()
            for (item in pending) {
                if (item.action == "DELETE") {
                    val ok = cloudSyncService.deleteProductFromCloud(item.barcode)
                    if (ok) localStorage.removePendingCatalogSync(item.barcode)
                } else if (item.action == "UPSERT" && item.productJson.isNotBlank()) {
                    try {
                        val prod = gson.fromJson(item.productJson, Product::class.java)
                        val ok = cloudSyncService.pushProductToCloud(prod)
                        if (ok) localStorage.removePendingCatalogSync(item.barcode)
                    } catch (_: Exception) {
                        localStorage.removePendingCatalogSync(item.barcode)
                    }
                }
            }
            syncWithCloud()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun saveOrUpdateProduct(product: Product) {
        saveOrUpdateProduct(product, null)
    }

    override suspend fun updateStock(barcode: String, quantitySold: Int) {
        val current = getProductByBarcode(barcode) ?: return
        val newStock = maxOf(0, current.currentStock - quantitySold)
        val updated = current.copy(currentStock = newStock)
        saveOrUpdateProduct(updated)
    }

    override suspend fun deleteProduct(barcode: String) {
        val currentList = _productsFlow.value.toMutableList()
        currentList.removeAll { it.barcode == barcode }
        _productsFlow.value = currentList

        scope.launch {
            cloudSyncService.deleteProductFromCloud(barcode)
        }
    }

    private suspend fun seedInitialDemoProducts() {
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val demoList = listOf(
            Product("77510001", "Coca Cola 500ml", "Bebidas", 8.0, 5.0, 50, 5, true, "Unidad", now),
            Product("77510002", "Sprite 500ml", "Bebidas", 8.0, 5.0, 35, 5, true, "Unidad", now),
            Product("77510003", "Agua Mineral 500ml", "Bebidas", 5.0, 3.0, 40, 5, true, "Unidad", now),
            Product("77520001", "Pipocas / Palomitas Grandes", "Snacks", 15.0, 4.0, 100, 10, true, "Porción", now),
            Product("77520002", "Pipocas / Palomitas Medianas", "Snacks", 10.0, 3.0, 100, 10, true, "Porción", now),
            Product("77520003", "Nachos con Queso", "Snacks", 18.0, 8.0, 25, 5, true, "Porción", now),
            Product("77530001", "Doritos 110g", "Golosinas", 12.0, 8.0, 30, 5, true, "Bolsa", now),
            Product("77530002", "Pringles Original", "Golosinas", 20.0, 14.0, 20, 3, true, "Tubo", now),
            Product("77530003", "Chocolatinas / M&M", "Golosinas", 10.0, 6.5, 45, 5, true, "Unidad", now)
        )
        localStorage.cacheProducts(demoList)
        _productsFlow.value = demoList
    }
}

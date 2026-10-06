package com.justplay.pos.core.interfaces

import com.justplay.pos.core.models.Product

/**
 * Inversión de Dependencias: Repositorio para consultar y actualizar el catálogo de productos.
 */
interface IProductRepository {
    suspend fun getProductByBarcode(barcode: String): Product?
    suspend fun getAllProducts(): List<Product>
    suspend fun searchProducts(query: String): List<Product>
    suspend fun saveOrUpdateProduct(product: Product)
    suspend fun updateStock(barcode: String, quantitySold: Int)
    suspend fun deleteProduct(barcode: String)
}

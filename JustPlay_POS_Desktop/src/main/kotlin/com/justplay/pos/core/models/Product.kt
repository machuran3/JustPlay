package com.justplay.pos.core.models

/**
 * Representa un producto en el catálogo comercial de JustPlay.
 */
data class Product(
    val barcode: String,
    val name: String,
    val category: String = "Snacks & Bebidas",
    val unitPrice: Double,
    val costPrice: Double = 0.0,
    val currentStock: Int = 0,
    val minStockAlert: Int = 5,
    val active: Boolean = true,
    val unitOfMeasure: String = "Unidad",
    val lastUpdated: String = ""
) {
    val isLowStock: Boolean
        get() = currentStock <= minStockAlert
}

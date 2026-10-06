package com.justplay.pos.core.models

/**
 * Representa una línea de producto agregada al carrito de ventas en curso.
 */
data class CartItem(
    val product: Product,
    val quantity: Int = 1
) {
    val subtotal: Double
        get() = product.unitPrice * quantity

    fun withQuantity(newQuantity: Int): CartItem {
        return copy(quantity = maxOf(1, newQuantity))
    }

    fun increment(): CartItem {
        return copy(quantity = quantity + 1)
    }

    fun decrement(): CartItem? {
        return if (quantity > 1) copy(quantity = quantity - 1) else null
    }
}

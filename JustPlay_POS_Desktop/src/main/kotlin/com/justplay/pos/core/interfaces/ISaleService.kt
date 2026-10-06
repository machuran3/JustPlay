package com.justplay.pos.core.interfaces

import com.justplay.pos.core.models.CartItem
import com.justplay.pos.core.models.PaymentMethod
import com.justplay.pos.core.models.Product
import com.justplay.pos.core.models.SaleTransaction
import kotlinx.coroutines.flow.StateFlow

/**
 * Single Responsibility: Lógica de negocio para manipulación del carrito, cálculos y checkout.
 */
interface ISaleService {
    val cartItemsFlow: StateFlow<List<CartItem>>
    val totalAmountFlow: StateFlow<Double>
    
    fun addProductByBarcode(product: Product, quantity: Int = 1)
    fun updateItemQuantity(barcode: String, quantity: Int)
    fun removeItem(barcode: String)
    fun clearCart()
    
    suspend fun checkoutSale(
        paymentMethod: PaymentMethod,
        cashReceived: Double = 0.0,
        clientName: String = "Venta Mostrador",
        clientPhone: String? = null,
        destinationType: String = "MOSTRADOR",
        targetRoomId: Int? = null,
        targetRoomName: String? = null,
        receiptImageUrl: String? = null
    ): Result<SaleTransaction>
}

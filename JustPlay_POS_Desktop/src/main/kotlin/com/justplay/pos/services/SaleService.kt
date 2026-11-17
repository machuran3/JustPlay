package com.justplay.pos.services

import com.justplay.pos.core.interfaces.ICloudSyncService
import com.justplay.pos.core.interfaces.ILocalStorage
import com.justplay.pos.core.interfaces.ISaleService
import com.justplay.pos.core.models.CartItem
import com.justplay.pos.core.models.PaymentMethod
import com.justplay.pos.core.models.Product
import com.justplay.pos.core.models.RoomOrder
import com.justplay.pos.core.models.SaleTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaleService(
    private val localStorage: ILocalStorage,
    private val cloudSyncService: ICloudSyncService,
    private val productCatalogService: ProductCatalogService,
    private val roomManagementService: RoomManagementService? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : ISaleService {

    private val _cartItemsFlow = MutableStateFlow<List<CartItem>>(emptyList())
    override val cartItemsFlow: StateFlow<List<CartItem>> = _cartItemsFlow.asStateFlow()

    override val totalAmountFlow: StateFlow<Double> = _cartItemsFlow.map { items ->
        items.sumOf { it.subtotal }
    }.stateIn(scope, SharingStarted.Eagerly, 0.0)

    override fun addProductByBarcode(product: Product, quantity: Int) {
        if (product.currentStock <= 0) return

        val current = _cartItemsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.product.barcode == product.barcode }

        if (index >= 0) {
            val existing = current[index]
            val maxAllowed = product.currentStock
            val newQty = (existing.quantity + quantity).coerceAtMost(maxAllowed)
            current[index] = existing.withQuantity(newQty)
        } else {
            val initialQty = quantity.coerceAtMost(product.currentStock)
            if (initialQty > 0) {
                current.add(CartItem(product = product, quantity = initialQty))
            }
        }

        _cartItemsFlow.value = current
    }

    override fun updateItemQuantity(barcode: String, quantity: Int) {
        if (quantity <= 0) {
            removeItem(barcode)
            return
        }
        val current = _cartItemsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.product.barcode == barcode }
        if (index >= 0) {
            val maxStock = current[index].product.currentStock
            val clampedQty = if (maxStock > 0) quantity.coerceAtMost(maxStock) else quantity
            current[index] = current[index].withQuantity(clampedQty)
            _cartItemsFlow.value = current
        }
    }

    override fun removeItem(barcode: String) {
        _cartItemsFlow.value = _cartItemsFlow.value.filterNot { it.product.barcode == barcode }
    }

    override fun clearCart() {
        _cartItemsFlow.value = emptyList()
    }

    override suspend fun checkoutSale(
        paymentMethod: PaymentMethod,
        cashReceived: Double,
        clientName: String,
        clientPhone: String?,
        destinationType: String,
        targetRoomId: Int?,
        targetRoomName: String?,
        receiptImageUrl: String?
    ): Result<SaleTransaction> {
        val items: List<CartItem>
        synchronized(this) {
            val current = _cartItemsFlow.value
            if (current.isEmpty()) {
                return Result.failure(IllegalStateException("El carrito de compras está vacío o la venta ya fue cobrada."))
            }
            val outOfStockItems = current.filter { it.product.currentStock <= 0 || it.quantity > it.product.currentStock }
            if (outOfStockItems.isNotEmpty()) {
                val names = outOfStockItems.joinToString(", ") { "${it.product.displayName} (Stock: ${it.product.currentStock})" }
                return Result.failure(IllegalStateException("No se puede cobrar: productos sin stock suficiente: $names (evita venta fantasma)"))
            }
            items = current.toList()
            clearCart()
        }

        val total = items.sumOf { it.subtotal }
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val saleId = "VENTA_PC_${System.currentTimeMillis()}"

        val actualCash: Double
        val actualQR: Double
        var change = 0.0

        when (paymentMethod) {
            PaymentMethod.EFECTIVO -> {
                actualCash = total
                actualQR = 0.0
                change = maxOf(0.0, cashReceived - total)
            }
            PaymentMethod.QR -> {
                actualCash = 0.0
                actualQR = total
                change = 0.0
            }
            PaymentMethod.MIXTO -> {
                actualCash = minOf(total, maxOf(0.0, cashReceived))
                actualQR = maxOf(0.0, total - actualCash)
                change = 0.0
            }
        }

        val transaction = SaleTransaction(
            id = saleId,
            dateTime = now,
            items = items,
            totalAmount = total,
            paymentMethod = paymentMethod,
            cashReceived = actualCash,
            qrAmount = actualQR,
            changeReturned = change,
            clientName = if (destinationType == "SALA" && targetRoomName != null) "Sala $targetRoomName" else clientName.ifBlank { "Venta Mostrador" },
            clientPhone = clientPhone,
            operatorName = "Lector POS PC",
            destinationType = destinationType,
            targetRoomId = targetRoomId,
            targetRoomName = targetRoomName,
            receiptImageUrl = receiptImageUrl,
            isSynced = false
        )

        try {
            // 1. Guardar en SQLite local inmediatamente
            localStorage.saveSale(transaction)

            // 2. Decrementar stock en catálogo local
            for (item in items) {
                productCatalogService.updateStock(item.product.barcode, item.quantity)
            }

            // 3. Si el destino es una sala, agregar consumo a la sala (sin duplicar registro de venta)
            if (destinationType == "SALA" && targetRoomId != null && roomManagementService != null) {
                for (item in items) {
                    val order = RoomOrder(
                        idFirebase = "ORD_${System.currentTimeMillis()}_${item.product.barcode}",
                        cantidad = item.quantity,
                        nombre = item.product.displayName,
                        precioUnidad = item.product.unitPrice,
                        estaEntregado = true,
                        barcode = item.product.barcode
                    )
                    roomManagementService.addOrderToRoom(targetRoomId, order, shouldRegisterInVentas = false)
                }
            }

            // 4. Limpiar carrito
            clearCart()

            // 5. Sincronizar en la nube con Firebase en segundo plano
            scope.launch {
                val synced = cloudSyncService.syncSale(transaction)
                if (synced) {
                    localStorage.markSaleAsSynced(transaction.id)
                }
            }

            return Result.success(transaction)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }
}

package com.justplay.pos.core.models

/**
 * Representa una venta finalizada para registrar en local (SQLite)
 * y sincronizar en la nube con Firebase (/registros y /auditoria_registros).
 */
data class SaleTransaction(
    val id: String,
    val dateTime: String,
    val items: List<CartItem>,
    val totalAmount: Double,
    val paymentMethod: PaymentMethod,
    val cashReceived: Double = 0.0,
    val qrAmount: Double = 0.0,
    val changeReturned: Double = 0.0,
    val clientName: String = "Venta Mostrador",
    val clientPhone: String? = null,
    val operatorName: String = "Lector POS PC",
    val destinationType: String = "MOSTRADOR", // "MOSTRADOR" o "SALA"
    val targetRoomId: Int? = null,
    val targetRoomName: String? = null,
    val receiptImageUrl: String? = null,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false
) {
    /**
     * Construye la cadena de resumen de pedidos compatible con JustPlay y AdminJustPlay.
     * Ejemplo: "2x Coca Cola 500ml (20.0 Bs), 1x Pipocas Grandes (15.0 Bs)"
     */
    val formattedSummary: String
        get() = items.joinToString(", ") { "${it.quantity}x ${it.product.name} (${it.subtotal} Bs)" }
}

enum class SyncStatus {
    ONLINE_SYNCED,
    OFFLINE_SAVED,
    SYNCING,
    ERROR
}

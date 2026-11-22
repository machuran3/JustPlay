package com.justplay.pos.ui.state

import com.justplay.pos.core.models.*
import com.justplay.pos.services.AutoUpdaterService

data class PosUiState(
    val cartItems: List<CartItem> = emptyList(),
    val totalAmount: Double = 0.0,
    val productsCatalog: List<Product> = emptyList(),
    val filteredProducts: List<Product> = emptyList(),
    val selectedCategory: String = "Todos",
    val searchQuery: String = "",
    val manualBarcodeText: String = "",
    val lastScannedBarcode: String? = null,
    val lastScannedProduct: Product? = null,
    val scanErrorMessage: String? = null,
    val isPaymentDialogOpen: Boolean = false,
    val isProductManagerOpen: Boolean = false,
    val selectedProductForEdit: Product? = null,
    val isHistoryDialogOpen: Boolean = false,
    val isSettingsDialogOpen: Boolean = false,
    val recentSales: List<SaleTransaction> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.ONLINE_SYNCED,
    val lastSaleCompleted: SaleTransaction? = null,
    val selectedPaymentMethod: PaymentMethod = PaymentMethod.EFECTIVO,
    val cashReceivedAmount: String = "",
    val clientNameInput: String = "Venta Mostrador",
    val clientPhoneInput: String = "",
    val serverBaseUrl: String = "https://justplay-a081e-default-rtdb.firebaseio.com",

    // Contexto de Destino de Venta (Mostrador o Sala)
    val saleDestinationType: String = "MOSTRADOR", // "MOSTRADOR" o "SALA"
    val selectedTargetRoomId: Int? = null,
    val selectedTargetRoomName: String? = null,

    // Módulo de Salas, Reservas y Navegación ("SALAS", "VOLANTES", "JUST_DANCE", "SCHEDULE", "POS")
    val currentTab: String = "SALAS",
    val rooms: List<Room> = emptyList(),
    val reservations: List<Reservation> = emptyList(),
    val sessionRecords: List<SessionRecord> = emptyList(),
    val selectedRoomForDetail: Room? = null,
    val roomPendingOrders: List<RoomOrder> = emptyList(),
    val roomActionMessage: String? = null,

    // Gestión y Traspaso de Reservas (Estilo Móvil)
    val isReservationDialogOpen: Boolean = false,
    val editingReservation: Reservation? = null,
    val defaultReservationCategory: String = "Minicine",
    val isAssignReservationDialogOpen: Boolean = false,
    val reservationToAssign: Reservation? = null,
    val prefillReservationForRoom: Reservation? = null,
    val viewReceiptForReservation: String? = null,

    // Tablero de Horarios / Cronograma Tipo Excel
    val scheduleSelectedDate: String = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
    val scheduleCategoryFilter: String = "TODOS", // "TODOS", "MINICINE", "VOLANTES", "JUST_DANCE"
    val scheduleViewMode: String = "GRID", // "GRID" (Cuadrícula Excel) o "TABLE" (Tabla Resumen)

    // Validación QR en tiempo real Móvil <-> PC (Venta Mostrador)
    val isWaitingQrValidation: Boolean = false,
    val qrValidationSolicitudId: String? = null,
    val qrValidationStatusMessage: String? = null,
    val saleReceiptImageUrl: String? = null,

    // Validación QR en tiempo real Móvil <-> PC (Salas / Volantes / Just Dance)
    val isWaitingRoomQrValidation: Boolean = false,
    val roomQrValidationSolicitudId: String? = null,
    val roomQrValidationStatusMessage: String? = null,
    val roomQrComprobanteUrl: String? = null,

    // Seguridad, Bloqueo de Dispositivo y Force Update
    val deviceId: String = "",
    val currentAppVersion: String = AutoUpdaterService.CURRENT_APP_VERSION,
    val minRequiredVersion: String = "1.0.0",
    val isDeviceRevoked: Boolean = false,
    val isVersionObsolete: Boolean = false,

    // Estados Anti-Spam y Exportación General
    val isProcessingSale: Boolean = false,
    val isRoomActionInProgress: Boolean = false,
    val isExportingMaster: Boolean = false,
    val masterExportNotification: String? = null,

    // Actualizaciones automáticas remotas (GitHub Releases)
    val availableUpdate: com.justplay.pos.services.ReleaseInfo? = null,
    val isCheckingForUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Float = 0f,
    val updateStatusMessage: String? = null,
    val isUpdateDownloaded: Boolean = false,
    val downloadedUpdateFile: java.io.File? = null
)

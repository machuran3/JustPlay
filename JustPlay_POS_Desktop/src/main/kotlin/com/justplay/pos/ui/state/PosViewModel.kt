package com.justplay.pos.ui.state

import com.justplay.pos.core.interfaces.IBarcodeScanner
import com.justplay.pos.core.interfaces.ICloudSyncService
import com.justplay.pos.core.interfaces.ILocalStorage
import com.justplay.pos.core.interfaces.ISaleService
import com.justplay.pos.core.models.PaymentMethod
import com.justplay.pos.core.models.Product
import com.justplay.pos.core.models.Reservation
import com.justplay.pos.core.models.Room
import com.justplay.pos.core.models.SyncStatus
import com.justplay.pos.infrastructure.security.DeviceSecurityService
import com.justplay.pos.core.utils.ReceiptImageUtils
import com.justplay.pos.services.ProductCatalogService
import com.justplay.pos.services.RoomManagementService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PosViewModel(
    private val barcodeScanner: IBarcodeScanner,
    private val productCatalogService: ProductCatalogService,
    private val saleService: ISaleService,
    private val roomManagementService: RoomManagementService,
    private val localStorage: ILocalStorage,
    private val cloudSyncService: ICloudSyncService,
    private val securityService: DeviceSecurityService? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val _uiState = MutableStateFlow(
        PosUiState(
            deviceId = securityService?.identity?.deviceId ?: "POS-PC-01",
            currentAppVersion = "1.0.0"
        )
    )
    val uiState: StateFlow<PosUiState> = _uiState.asStateFlow()

    private val isCheckingOut = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastCheckoutTimestamp = 0L

    private val isStartingRoom = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastRoomActionTimestamp = 0L

    init {
        // Escuchar escaneos globales de la pistola de códigos de barras
        scope.launch {
            barcodeScanner.barcodeScannedFlow.collect { scannedBarcode ->
                onBarcodeScanned(scannedBarcode)
            }
        }

        // Escuchar cambios en el carrito
        scope.launch {
            saleService.cartItemsFlow.collect { items ->
                _uiState.update { it.copy(cartItems = items) }
            }
        }

        // Escuchar el total de la venta
        scope.launch {
            saleService.totalAmountFlow.collect { total ->
                _uiState.update { it.copy(totalAmount = total) }
            }
        }

        // Escuchar el catálogo de productos
        scope.launch {
            productCatalogService.productsFlow.collect { products ->
                _uiState.update {
                    it.copy(
                        productsCatalog = products,
                        filteredProducts = filterProducts(products, it.selectedCategory, it.searchQuery)
                    )
                }
            }
        }

        // Escuchar el estado de sincronización con Firebase
        scope.launch {
            cloudSyncService.syncStatusFlow.collect { status ->
                _uiState.update { it.copy(syncStatus = status) }
            }
        }

        // Escuchar el estado de las salas en tiempo real
        scope.launch {
            roomManagementService.roomsFlow.collect { roomList ->
                _uiState.update { current ->
                    val updatedDetail = if (current.selectedRoomForDetail != null) {
                        roomList.find { it.id == current.selectedRoomForDetail.id } ?: current.selectedRoomForDetail
                    } else null
                    current.copy(rooms = roomList, selectedRoomForDetail = updatedDetail)
                }
            }
        }

        // Escuchar las reservas en tiempo real
        scope.launch {
            roomManagementService.reservationsFlow.collect { resList ->
                _uiState.update { it.copy(reservations = resList) }
            }
        }

        // Escuchar los registros históricos de sesiones en tiempo real
        scope.launch {
            roomManagementService.recordsFlow.collect { recList ->
                _uiState.update { it.copy(sessionRecords = recList) }
            }
        }

        // Inicializar fecha para el cronograma (Por defecto: "TODAS" para ver todas las reservas)
        _uiState.update { it.copy(scheduleSelectedDate = "TODAS") }

        // Cargar precios base configurados en Firebase
        scope.launch {
            try {
                val prices = cloudSyncService.fetchBasePrices()
                com.justplay.pos.core.models.BasePriceConfig.updateFromMap(prices)
            } catch (_: Exception) {}
        }

        // Escuchar estados de seguridad y control de versiones
        if (securityService != null) {
            scope.launch {
                securityService.isDeviceRevokedFlow.collect { revoked ->
                    _uiState.update { it.copy(isDeviceRevoked = revoked) }
                }
            }

            scope.launch {
                securityService.isVersionObsoleteFlow.collect { obsolete ->
                    _uiState.update { it.copy(isVersionObsolete = obsolete) }
                }
            }

            scope.launch {
                securityService.minRequiredVersionFlow.collect { minVer ->
                    _uiState.update { it.copy(minRequiredVersion = minVer) }
                }
            }

            // Iniciar Heartbeat automático
            securityService.startHeartbeat()
        }

        // Iniciar scanner y cargar historial local
        barcodeScanner.startListening()
        loadRecentSales()
        startAutomaticConnectivityAndSyncMonitor()
    }

    private fun startAutomaticConnectivityAndSyncMonitor() {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                kotlinx.coroutines.delay(10000)
                try {
                    val isOnline = cloudSyncService.isPosAuthorized()
                    if (isOnline) {
                        val pendingSales = localStorage.getUnsyncedSales()
                        val pendingCatalog = localStorage.getPendingCatalogSync()
                        if (pendingSales.isNotEmpty() || pendingCatalog.isNotEmpty()) {
                            _uiState.update { it.copy(syncStatus = SyncStatus.SYNCING) }
                            cloudSyncService.syncPendingOfflineSales(localStorage)
                            productCatalogService.syncPendingCatalogWithCloud()
                            loadRecentSales()
                        }
                        _uiState.update { it.copy(syncStatus = SyncStatus.ONLINE_SYNCED) }
                    } else {
                        _uiState.update { it.copy(syncStatus = SyncStatus.OFFLINE_SAVED) }
                    }
                } catch (_: Exception) {
                    _uiState.update { it.copy(syncStatus = SyncStatus.OFFLINE_SAVED) }
                }
            }
        }
    }

    fun switchTab(tab: String) {
        _uiState.update {
            it.copy(
                currentTab = tab,
                selectedPaymentMethod = if (tab == "POS") PaymentMethod.EFECTIVO else it.selectedPaymentMethod
            )
        }
        if (tab == "SCHEDULE") {
            refreshSchedule()
        }
    }

    fun setScheduleSelectedDate(date: String) {
        _uiState.update { it.copy(scheduleSelectedDate = date) }
    }

    fun setScheduleCategoryFilter(cat: String) {
        _uiState.update { it.copy(scheduleCategoryFilter = cat) }
    }

    fun setScheduleViewMode(mode: String) {
        _uiState.update { it.copy(scheduleViewMode = mode) }
    }

    fun refreshSchedule() {
        scope.launch {
            roomManagementService.refreshRooms()
        }
    }

    fun onEnterKeyPressed() {
        val now = System.currentTimeMillis()
        if (now - lastCheckoutTimestamp < 1200L || now - lastRoomActionTimestamp < 1200L) return
        val state = _uiState.value
        if (state.isProcessingSale || isCheckingOut.get() || state.isRoomActionInProgress || isStartingRoom.get()) return

        if (state.isPaymentDialogOpen) {
            confirmCheckout()
        } else if (state.cartItems.isNotEmpty() && state.currentTab == "POS" && state.selectedRoomForDetail == null && !state.isProductManagerOpen) {
            openPaymentDialog()
        }
    }

    fun onBarcodeScanned(barcode: String) {
        scope.launch {
            val product = productCatalogService.getProductByBarcode(barcode)
            val currentState = _uiState.value

            // Limpieza defensiva en caso de que algún campo de texto de venta mostrador tuviera el foco
            fun cleanInput(field: String): String {
                if (field.isBlank()) return field
                var s = field.trim()
                if (s == barcode) return ""
                if (s.endsWith(barcode)) s = s.removeSuffix(barcode).trimEnd()
                for (len in minOf(6, barcode.length) downTo 1) {
                    val p = barcode.take(len)
                    if (s == p) return ""
                    if (s.endsWith(p)) {
                        s = s.removeSuffix(p).trimEnd()
                        break
                    }
                }
                return s
            }
            val cleanClientName = cleanInput(currentState.clientNameInput)
            val cleanSearchQuery = cleanInput(currentState.searchQuery)

            if (product != null && product.active) {
                if (product.currentStock <= 0) {
                    _uiState.update {
                        it.copy(
                            lastScannedBarcode = barcode,
                            lastScannedProduct = product,
                            scanErrorMessage = "⚠️ PRODUCTO AGOTADO: '${product.name}' (Stock: 0). No se puede vender sin stock (evita venta fantasma).",
                            manualBarcodeText = "",
                            searchQuery = cleanSearchQuery
                        )
                    }
                    return@launch
                }

                val inCart = saleService.cartItemsFlow.value.find { it.product.barcode == product.barcode }?.quantity ?: 0
                val openRoom = currentState.selectedRoomForDetail

                if (openRoom == null && inCart >= product.currentStock) {
                    _uiState.update {
                        it.copy(
                            lastScannedBarcode = barcode,
                            lastScannedProduct = product,
                            scanErrorMessage = "⚠️ Stock máximo alcanzado para '${product.name}' (${product.currentStock} disponibles en inventario).",
                            manualBarcodeText = "",
                            searchQuery = cleanSearchQuery
                        )
                    }
                    return@launch
                }

                try {
                    Toolkit.getDefaultToolkit().beep()
                } catch (_: Exception) {}

                if (openRoom != null) {
                    // Agregar producto directamente a la sala abierta
                    addProductToOpenRoom(product, 1)
                } else {
                    // Si no estoy en sala, ir DIRECTO a venta de mostrador (POS) y agregar producto
                    saleService.addProductByBarcode(product, 1)
                    _uiState.update {
                        it.copy(
                            currentTab = "POS", // Cambia a venta de mostrador automáticamente
                            lastScannedBarcode = barcode,
                            lastScannedProduct = product,
                            scanErrorMessage = null,
                            manualBarcodeText = "",
                            clientNameInput = if (cleanClientName.isBlank()) "Venta Mostrador" else cleanClientName,
                            searchQuery = cleanSearchQuery
                        )
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        lastScannedBarcode = barcode,
                        lastScannedProduct = null,
                        scanErrorMessage = "Código no encontrado: '$barcode'. Pulsa '➕ Crear Producto' para registrarlo.",
                        manualBarcodeText = barcode,
                        clientNameInput = if (cleanClientName.isBlank()) "Venta Mostrador" else cleanClientName,
                        searchQuery = cleanSearchQuery
                    )
                }
            }
        }
    }

    fun onManualBarcodeSubmit() {
        val text = _uiState.value.manualBarcodeText.trim()
        if (text.isNotEmpty()) {
            barcodeScanner.simulateBarcodeScan(text)
        }
    }

    fun updateManualBarcodeText(text: String) {
        _uiState.update { it.copy(manualBarcodeText = text) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                filteredProducts = filterProducts(it.productsCatalog, it.selectedCategory, query)
            )
        }
    }

    fun onCategorySelected(category: String) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                filteredProducts = filterProducts(it.productsCatalog, category, it.searchQuery)
            )
        }
    }

    fun onProductCardClicked(product: Product) {
        if (product.currentStock <= 0) {
            _uiState.update {
                it.copy(
                    scanErrorMessage = "⚠️ PRODUCTO AGOTADO: '${product.name}' (Stock: 0). No se puede vender sin inventario (evita venta fantasma)."
                )
            }
            return
        }
        val inCart = saleService.cartItemsFlow.value.find { it.product.barcode == product.barcode }?.quantity ?: 0
        if (inCart >= product.currentStock) {
            _uiState.update {
                it.copy(
                    scanErrorMessage = "⚠️ Stock máximo alcanzado para '${product.name}' (${product.currentStock} disponibles en inventario)."
                )
            }
            return
        }
        saleService.addProductByBarcode(product, 1)
    }

    fun onUpdateCartQuantity(barcode: String, newQuantity: Int) {
        saleService.updateItemQuantity(barcode, newQuantity)
    }

    fun onRemoveCartItem(barcode: String) {
        saleService.removeItem(barcode)
    }

    fun onClearCart() {
        saleService.clearCart()
    }

    fun setSaleDestination(destination: String) {
        _uiState.update {
            it.copy(
                saleDestinationType = destination,
                selectedTargetRoomId = if (destination == "MOSTRADOR") null else it.selectedTargetRoomId,
                selectedTargetRoomName = if (destination == "MOSTRADOR") null else it.selectedTargetRoomName
            )
        }
    }

    fun setTargetRoom(roomId: Int?, roomName: String?) {
        _uiState.update {
            it.copy(
                selectedTargetRoomId = roomId,
                selectedTargetRoomName = roomName
            )
        }
    }

    fun openPaymentDialog() {
        _uiState.update {
            it.copy(
                isPaymentDialogOpen = true,
                selectedPaymentMethod = PaymentMethod.EFECTIVO,
                cashReceivedAmount = String.format("%.2f", it.totalAmount),
                saleDestinationType = "MOSTRADOR",
                selectedTargetRoomId = null,
                selectedTargetRoomName = null,
                saleReceiptImageUrl = null
            )
        }
    }

    fun closePaymentDialog() {
        _uiState.update { it.copy(isPaymentDialogOpen = false, saleReceiptImageUrl = null) }
    }

    fun onPaymentMethodChanged(method: PaymentMethod) {
        _uiState.update { it.copy(selectedPaymentMethod = method) }
    }

    fun onCashReceivedChanged(amountStr: String) {
        _uiState.update { it.copy(cashReceivedAmount = amountStr) }
    }

    fun onClientNameChanged(name: String) {
        _uiState.update { it.copy(clientNameInput = name) }
    }

    fun onClientPhoneChanged(phone: String) {
        _uiState.update { it.copy(clientPhoneInput = phone) }
    }

    fun pickAndAttachSalePhotos() {
        val files = ReceiptImageUtils.pickImagesFromNativeDialog("Seleccionar Comprobante(s) para Venta Mostrador")
        if (files.isNotEmpty()) {
            val payloads = files.map { ReceiptImageUtils.processAndCompressImageFile(it) }
            val merged = ReceiptImageUtils.addReceiptUrls(_uiState.value.saleReceiptImageUrl, payloads)
            _uiState.update { it.copy(saleReceiptImageUrl = merged) }
        }
    }

    fun updateSaleReceiptUrl(newReceiptString: String?) {
        _uiState.update { it.copy(saleReceiptImageUrl = newReceiptString) }
    }

    fun requestQrValidationFromMobile() {
        scope.launch {
            val state = _uiState.value
            val tempSaleId = "VENTA_PC_${System.currentTimeMillis()}"
            val cash = state.cashReceivedAmount.toDoubleOrNull() ?: 0.0
            val qr = if (state.selectedPaymentMethod == PaymentMethod.QR) state.totalAmount else maxOf(0.0, state.totalAmount - cash)

            val solicitudId = cloudSyncService.emitQrValidationRequest(
                saleId = tempSaleId,
                tipo = state.saleDestinationType,
                salaId = state.selectedTargetRoomId,
                salaNombre = state.selectedTargetRoomName ?: "Mostrador",
                cliente = state.clientNameInput.ifBlank { "Cliente" },
                montoTotal = state.totalAmount,
                montoEfectivo = cash,
                montoQR = qr,
                metodoPago = state.selectedPaymentMethod.displayName
            )

            _uiState.update {
                it.copy(
                    isWaitingQrValidation = true,
                    qrValidationSolicitudId = solicitudId,
                    qrValidationStatusMessage = "🔔 Solicitud enviada a celulares de Admin y Empleados.\nEsperando comprobante de pago..."
                )
            }

            // Polling de respuesta en tiempo real
            var intentos = 0
            while (intentos < 120 && _uiState.value.isWaitingQrValidation) {
                kotlinx.coroutines.delay(1500)
                intentos++
                val data = cloudSyncService.checkQrValidationStatus(solicitudId)
                if (data != null) {
                    val estado = data["estado"] as? String
                    if (estado == "APROBADO") {
                        val comprobante = (data["comprobanteUrl"] ?: data["comprobanteUri"]) as? String
                        val merged = ReceiptImageUtils.addReceiptUrls(_uiState.value.saleReceiptImageUrl, listOfNotNull(comprobante))
                        _uiState.update {
                            it.copy(
                                isWaitingQrValidation = false,
                                qrValidationSolicitudId = null,
                                qrValidationStatusMessage = "✅ ¡Pago QR y Comprobante(s) Aprobados desde el Móvil!",
                                saleReceiptImageUrl = merged
                            )
                        }
                        confirmCheckout(receiptImageUrl = merged)
                        break
                    } else if (estado == "RECHAZADO") {
                        _uiState.update {
                            it.copy(
                                isWaitingQrValidation = false,
                                qrValidationSolicitudId = null,
                                qrValidationStatusMessage = "❌ El pago QR fue rechazado desde el móvil."
                            )
                        }
                        break
                    }
                }
            }
        }
    }

    fun cancelQrValidation() {
        val id = _uiState.value.qrValidationSolicitudId
        if (id != null) {
            scope.launch {
                cloudSyncService.cancelQrValidationRequest(id)
            }
        }
        _uiState.update {
            it.copy(
                isWaitingQrValidation = false,
                qrValidationSolicitudId = null,
                qrValidationStatusMessage = null
            )
        }
    }

    fun confirmCheckout(receiptImageUrl: String? = null) {
        val now = System.currentTimeMillis()
        if (now - lastCheckoutTimestamp < 1200L) return
        if (!isCheckingOut.compareAndSet(false, true)) return
        lastCheckoutTimestamp = now

        val state = _uiState.value
        if (state.cartItems.isEmpty()) {
            isCheckingOut.set(false)
            _uiState.update { it.copy(isPaymentDialogOpen = false) }
            return
        }

        val cash = state.cashReceivedAmount.toDoubleOrNull() ?: state.totalAmount
        if (state.selectedPaymentMethod == PaymentMethod.EFECTIVO && cash < state.totalAmount) {
            isCheckingOut.set(false)
            _uiState.update {
                it.copy(scanErrorMessage = "El monto en efectivo no puede ser menor al total.")
            }
            return
        }

        val finalReceipt = receiptImageUrl ?: state.saleReceiptImageUrl

        // CERRAR VENTANA INMEDIATAMENTE para respuesta instantánea de UI y evitar re-entradas por Enter
        _uiState.update {
            it.copy(
                isPaymentDialogOpen = false,
                isProcessingSale = true,
                scanErrorMessage = null,
                isWaitingQrValidation = false,
                qrValidationSolicitudId = null,
                qrValidationStatusMessage = null,
                saleReceiptImageUrl = null
            )
        }

        scope.launch {
            try {
                val isAuthorized = cloudSyncService.isPosAuthorized()
                if (!isAuthorized) {
                    _uiState.update {
                        it.copy(
                            scanErrorMessage = "⛔ Terminal POS Bloqueada por el Administrador. Solicita autorización en AdminJustPlay."
                        )
                    }
                    return@launch
                }

                val result = saleService.checkoutSale(
                    paymentMethod = state.selectedPaymentMethod,
                    cashReceived = cash,
                    clientName = state.clientNameInput,
                    clientPhone = state.clientPhoneInput.ifBlank { null },
                    destinationType = state.saleDestinationType,
                    targetRoomId = state.selectedTargetRoomId,
                    targetRoomName = state.selectedTargetRoomName,
                    receiptImageUrl = finalReceipt
                )

                if (result.isSuccess) {
                    val sale = result.getOrNull()
                    _uiState.update {
                        it.copy(
                            lastSaleCompleted = sale,
                            cartItems = emptyList(),
                            totalAmount = 0.0,
                            scanErrorMessage = null
                        )
                    }
                    loadRecentSales()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Error al procesar la venta"
                    _uiState.update {
                        it.copy(scanErrorMessage = "❌ $errorMsg")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(scanErrorMessage = "❌ Error: ${e.message}")
                }
            } finally {
                kotlinx.coroutines.delay(1000L)
                isCheckingOut.set(false)
                _uiState.update { it.copy(isProcessingSale = false) }
            }
        }
    }

    // GESTIÓN DE SALAS UNIFICADA (CLIC DIRECTO ESTILO MÓVIL)
    fun openRoomDetail(room: Room, prefill: Reservation? = null) {
        _uiState.update {
            it.copy(
                selectedRoomForDetail = room,
                prefillReservationForRoom = prefill,
                roomPendingOrders = emptyList(),
                isWaitingRoomQrValidation = false,
                roomQrValidationSolicitudId = null,
                roomQrValidationStatusMessage = null,
                roomQrComprobanteUrl = prefill?.comprobanteUri,
                scanErrorMessage = null
            )
        }
    }

    fun closeRoomDetail() {
        val solicitudId = _uiState.value.roomQrValidationSolicitudId
        if (solicitudId != null) {
            scope.launch { cloudSyncService.cancelQrValidationRequest(solicitudId) }
        }
        _uiState.update {
            it.copy(
                selectedRoomForDetail = null,
                prefillReservationForRoom = null,
                roomPendingOrders = emptyList(),
                isWaitingRoomQrValidation = false,
                roomQrValidationSolicitudId = null,
                roomQrValidationStatusMessage = null,
                roomQrComprobanteUrl = null,
                scanErrorMessage = null
            )
        }
    }

    fun requestRoomQrValidation(
        room: Room,
        client: String,
        phone: String,
        gameOrMovie: String,
        durationMin: Int,
        totalAmount: Double,
        montoEfectivo: Double,
        montoQR: Double,
        metodoPago: String,
        pedidos: List<com.justplay.pos.core.models.RoomOrder> = emptyList()
    ) {
        scope.launch {
            val tempSaleId = "SALA_${room.id}_${System.currentTimeMillis()}"
            val solicitudId = cloudSyncService.emitQrValidationRequest(
                saleId = tempSaleId,
                tipo = room.categoria,
                salaId = room.id,
                salaNombre = room.nombre,
                cliente = client.ifBlank { "Cliente Sala" },
                montoTotal = totalAmount,
                montoEfectivo = montoEfectivo,
                montoQR = montoQR,
                metodoPago = metodoPago
            )

            _uiState.update {
                it.copy(
                    isWaitingRoomQrValidation = true,
                    roomQrValidationSolicitudId = solicitudId,
                    roomQrValidationStatusMessage = "🔔 Solicitud enviada a celulares de Admin y Empleados.\nEsperando comprobante de pago..."
                )
            }

            // Polling de respuesta en tiempo real
            var intentos = 0
            while (intentos < 120 && _uiState.value.isWaitingRoomQrValidation) {
                kotlinx.coroutines.delay(1500)
                intentos++
                val data = cloudSyncService.checkQrValidationStatus(solicitudId)
                if (data != null) {
                    val estado = data["estado"] as? String
                    if (estado == "APROBADO") {
                        val comprobante = (data["comprobanteUrl"] ?: data["comprobanteUri"]) as? String
                        val merged = ReceiptImageUtils.addReceiptUrls(_uiState.value.roomQrComprobanteUrl, listOfNotNull(comprobante))
                        _uiState.update {
                            it.copy(
                                isWaitingRoomQrValidation = false,
                                roomQrValidationSolicitudId = null,
                                roomQrComprobanteUrl = merged,
                                roomQrValidationStatusMessage = "✅ ¡Comprobante(s) Aprobado(s) desde el Móvil!"
                            )
                        }
                        break
                    } else if (estado == "RECHAZADO") {
                        _uiState.update {
                            it.copy(
                                isWaitingRoomQrValidation = false,
                                roomQrValidationSolicitudId = null,
                                roomQrValidationStatusMessage = "❌ Solicitud de comprobante rechazada desde el móvil."
                            )
                        }
                        break
                    }
                }
            }
        }
    }

    fun cancelRoomQrValidation() {
        val id = _uiState.value.roomQrValidationSolicitudId
        if (id != null) {
            scope.launch {
                cloudSyncService.cancelQrValidationRequest(id)
            }
        }
        _uiState.update {
            it.copy(
                isWaitingRoomQrValidation = false,
                roomQrValidationSolicitudId = null,
                roomQrValidationStatusMessage = null
            )
        }
    }

    fun pickAndAttachRoomPhotos() {
        val files = ReceiptImageUtils.pickImagesFromNativeDialog("Seleccionar Comprobante(s) para Sala")
        if (files.isNotEmpty()) {
            val payloads = files.map { ReceiptImageUtils.processAndCompressImageFile(it) }
            val merged = ReceiptImageUtils.addReceiptUrls(_uiState.value.roomQrComprobanteUrl, payloads)
            _uiState.update { it.copy(roomQrComprobanteUrl = merged) }
        }
    }

    fun updateRoomReceiptUrl(newReceiptString: String?) {
        _uiState.update { it.copy(roomQrComprobanteUrl = newReceiptString) }
    }

    fun pickAndAttachPhotosToActiveRoom(roomId: Int) {
        val files = ReceiptImageUtils.pickImagesFromNativeDialog("Agregar Comprobante(s) a la Sala")
        if (files.isNotEmpty()) {
            val payloads = files.map { ReceiptImageUtils.processAndCompressImageFile(it) }
            val room = _uiState.value.rooms.find { it.id == roomId } ?: return
            val merged = ReceiptImageUtils.addReceiptUrls(room.comprobanteUri, payloads)
            updateActiveRoomComprobante(roomId, merged)
        }
    }

    fun updateActiveRoomComprobante(roomId: Int, newReceiptString: String?) {
        scope.launch {
            roomManagementService.updateRoomComprobante(roomId, newReceiptString)
            _uiState.update { current ->
                val updatedRooms = current.rooms.map { if (it.id == roomId) it.copy(comprobanteUri = newReceiptString) else it }
                val updatedSelected = if (current.selectedRoomForDetail?.id == roomId) {
                    current.selectedRoomForDetail.copy(comprobanteUri = newReceiptString)
                } else current.selectedRoomForDetail
                current.copy(rooms = updatedRooms, selectedRoomForDetail = updatedSelected)
            }
        }
    }


    fun addProductToOpenRoom(product: Product, quantity: Int = 1) {
        val currentRoom = _uiState.value.selectedRoomForDetail ?: return
        if (product.currentStock <= 0) {
            _uiState.update {
                it.copy(roomActionMessage = "⚠️ PRODUCTO AGOTADO: '${product.displayName}' (Stock: 0). No se puede vender.")
            }
            return
        }

        val existingInRoom = if (currentRoom.estado == com.justplay.pos.core.models.RoomState.OCUPADA) {
            currentRoom.pedidos.filter { it.nombre.equals(product.displayName, ignoreCase = true) || it.nombre.equals(product.name, ignoreCase = true) }.sumOf { it.cantidad }
        } else {
            _uiState.value.roomPendingOrders.filter { it.nombre.equals(product.displayName, ignoreCase = true) || it.nombre.equals(product.name, ignoreCase = true) }.sumOf { it.cantidad }
        }

        if (existingInRoom + quantity > product.currentStock) {
            _uiState.update {
                it.copy(roomActionMessage = "⚠️ Stock insuficiente de '${product.displayName}' (Stock: ${product.currentStock}, ya agregados: $existingInRoom).")
            }
            return
        }

        val newOrder = com.justplay.pos.core.models.RoomOrder(
            idFirebase = "ORD_${System.currentTimeMillis()}",
            cantidad = quantity,
            nombre = product.displayName,
            precioUnidad = product.unitPrice,
            estaEntregado = true
        )

        if (currentRoom.estado == com.justplay.pos.core.models.RoomState.OCUPADA) {
            scope.launch {
                val ok = roomManagementService.addOrderToRoom(currentRoom.id, newOrder)
                if (ok) {
                    productCatalogService.updateStock(product.barcode, -quantity)
                    _uiState.update {
                        val updatedList = currentRoom.pedidos + newOrder
                        it.copy(
                            selectedRoomForDetail = currentRoom.copy(pedidos = updatedList),
                            roomActionMessage = "🍿 Consumo agregado: ${product.displayName}"
                        )
                    }
                }
            }
        } else {
            // Sala disponible: almacenar en pedidos temporales antes de iniciar
            _uiState.update {
                it.copy(
                    roomPendingOrders = it.roomPendingOrders + newOrder,
                    roomActionMessage = "🍿 Consumo pre-agregado: ${product.displayName}"
                )
            }
        }
    }

    fun removeProductFromOpenRoom(index: Int) {
        val currentRoom = _uiState.value.selectedRoomForDetail ?: return
        if (currentRoom.estado == com.justplay.pos.core.models.RoomState.OCUPADA) {
            scope.launch {
                val ok = roomManagementService.removeOrderFromRoom(currentRoom.id, index)
                if (ok) {
                    val updatedList = currentRoom.pedidos.toMutableList()
                    if (index in updatedList.indices) updatedList.removeAt(index)
                    _uiState.update {
                        it.copy(selectedRoomForDetail = currentRoom.copy(pedidos = updatedList))
                    }
                }
            }
        } else {
            _uiState.update {
                val updatedList = it.roomPendingOrders.toMutableList()
                if (index in updatedList.indices) updatedList.removeAt(index)
                it.copy(roomPendingOrders = updatedList)
            }
        }
    }

    fun confirmStartRoom(
        client: String,
        phone: String,
        gameOrMovie: String,
        durationMin: Int,
        price: Double,
        paymentMethod: String,
        pedidos: List<com.justplay.pos.core.models.RoomOrder> = emptyList(),
        receiptImageUrl: String? = null,
        associatedReservationId: String? = null
    ) {
        val now = System.currentTimeMillis()
        if (now - lastRoomActionTimestamp < 1500L) return
        if (!isStartingRoom.compareAndSet(false, true)) return
        lastRoomActionTimestamp = now

        val room = _uiState.value.selectedRoomForDetail ?: run {
            isStartingRoom.set(false)
            return
        }

        val resIdToDelete = associatedReservationId ?: _uiState.value.prefillReservationForRoom?.idFirebase

        // CERRAR VENTANA INMEDIATAMENTE para respuesta instantánea de UI
        closeRoomDetail()
        _uiState.update { it.copy(isRoomActionInProgress = true) }

        val finalReceipt = receiptImageUrl ?: _uiState.value.roomQrComprobanteUrl
        scope.launch {
            try {
                val ok = roomManagementService.startRoom(
                    roomId = room.id,
                    clientName = client,
                    clientPhone = phone,
                    gameOrMovie = gameOrMovie,
                    durationMinutes = durationMin,
                    price = price,
                    paymentMethod = paymentMethod,
                    pedidos = pedidos,
                    receiptImageUrl = finalReceipt
                )
                if (ok) {
                    if (!resIdToDelete.isNullOrBlank()) {
                        roomManagementService.deleteReservation(resIdToDelete)
                    }
                    _uiState.update { it.copy(roomActionMessage = "▶ Sala ${room.nombre} iniciada con éxito") }
                } else {
                    _uiState.update { it.copy(roomActionMessage = "⚠️ No se pudo iniciar la sala ${room.nombre}") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(roomActionMessage = "❌ Error: ${e.message}") }
            } finally {
                kotlinx.coroutines.delay(1000L)
                isStartingRoom.set(false)
                _uiState.update { it.copy(isRoomActionInProgress = false) }
            }
        }
    }

    fun confirmAddTime(roomId: Int, extraMinutes: Int) {
        if (_uiState.value.isRoomActionInProgress) return
        _uiState.update { it.copy(isRoomActionInProgress = true) }
        scope.launch {
            try {
                val ok = roomManagementService.addTimeToRoom(roomId, extraMinutes)
                if (ok) {
                    _uiState.update { it.copy(roomActionMessage = "➕ Se agregaron $extraMinutes min") }
                }
            } finally {
                kotlinx.coroutines.delay(500)
                _uiState.update { it.copy(isRoomActionInProgress = false) }
            }
        }
    }

    fun clearRoom(room: Room) {
        val now = System.currentTimeMillis()
        if (now - lastRoomActionTimestamp < 1500L) return
        if (!isStartingRoom.compareAndSet(false, true)) return
        lastRoomActionTimestamp = now

        // CERRAR VENTANA INMEDIATAMENTE
        closeRoomDetail()
        _uiState.update { it.copy(isRoomActionInProgress = true) }

        scope.launch {
            try {
                val ok = roomManagementService.clearRoom(room.id)
                if (ok) {
                    _uiState.update { it.copy(roomActionMessage = "🛑 Sala ${room.nombre} finalizada y liberada") }
                } else {
                    _uiState.update { it.copy(roomActionMessage = "⚠️ No se pudo finalizar la sala ${room.nombre}") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(roomActionMessage = "❌ Error: ${e.message}") }
            } finally {
                kotlinx.coroutines.delay(1000L)
                isStartingRoom.set(false)
                _uiState.update { it.copy(isRoomActionInProgress = false) }
            }
        }
    }

    fun updateActiveRoomSession(
        roomId: Int,
        clientName: String,
        clientPhone: String,
        gameOrMovie: String,
        price: Double,
        extraDurationMinutes: Int? = null,
        newPaymentMethod: String? = null
    ) {
        if (_uiState.value.isRoomActionInProgress) return
        _uiState.update { it.copy(isRoomActionInProgress = true) }
        scope.launch {
            try {
                val ok = roomManagementService.updateRoomSession(
                    roomId = roomId,
                    clientName = clientName,
                    clientPhone = clientPhone,
                    gameOrMovie = gameOrMovie,
                    price = price,
                    extraDurationMinutes = extraDurationMinutes,
                    newPaymentMethod = newPaymentMethod
                )
                if (ok) {
                    _uiState.update { current ->
                        val updatedRooms = current.rooms.map { r ->
                            if (r.id == roomId) {
                                r.copy(
                                    cliente = clientName.trim(),
                                    celular = clientPhone.trim(),
                                    pelicula = gameOrMovie.trim(),
                                    precioCobrado = price,
                                    metodoPago = newPaymentMethod ?: r.metodoPago
                                )
                            } else r
                        }
                        current.copy(
                            rooms = updatedRooms,
                            roomActionMessage = "✅ Parámetros de la sala actualizados correctamente",
                            selectedRoomForDetail = null
                        )
                    }
                } else {
                    _uiState.update { it.copy(roomActionMessage = "❌ Error al actualizar los parámetros de la sala") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(roomActionMessage = "❌ Error: ${e.message}") }
            } finally {
                kotlinx.coroutines.delay(500)
                _uiState.update { it.copy(isRoomActionInProgress = false) }
            }
        }
    }

    fun refreshRooms() {
        scope.launch {
            roomManagementService.refreshRooms()
            try {
                val prices = cloudSyncService.fetchBasePrices()
                com.justplay.pos.core.models.BasePriceConfig.updateFromMap(prices)
            } catch (_: Exception) {}
        }
    }

    fun openProductManager(productForEdit: Product? = null) {
        _uiState.update {
            it.copy(
                isProductManagerOpen = true,
                selectedProductForEdit = productForEdit
            )
        }
    }

    fun closeProductManager() {
        _uiState.update {
            it.copy(
                isProductManagerOpen = false,
                selectedProductForEdit = null
            )
        }
    }

    fun saveProduct(product: Product, oldBarcode: String? = null) {
        scope.launch {
            productCatalogService.saveOrUpdateProduct(product, oldBarcode)
            closeProductManager()
        }
    }

    fun deleteProduct(barcode: String) {
        scope.launch {
            productCatalogService.deleteProduct(barcode)
            closeProductManager()
        }
    }

    fun openHistoryDialog() {
        loadRecentSales()
        _uiState.update { it.copy(isHistoryDialogOpen = true) }
    }

    fun closeHistoryDialog() {
        _uiState.update { it.copy(isHistoryDialogOpen = false) }
    }

    fun openSettingsDialog() {
        _uiState.update { it.copy(isSettingsDialogOpen = true) }
    }

    fun closeSettingsDialog() {
        _uiState.update { it.copy(isSettingsDialogOpen = false) }
    }

    fun triggerCloudSync() {
        scope.launch {
            cloudSyncService.syncPendingOfflineSales(localStorage)
            productCatalogService.syncWithCloud()
            roomManagementService.refreshRooms()
            loadRecentSales()
        }
    }

    fun checkDeviceAuthorization() {
        scope.launch {
            securityService?.refreshSecurityStatus()
        }
    }

    fun loadRecentSales() {
        scope.launch {
            val sales = localStorage.getAllSales(50)
            _uiState.update { it.copy(recentSales = sales) }
        }
    }

    fun exportMasterReport() {
        if (_uiState.value.isExportingMaster) return
        _uiState.update { it.copy(isExportingMaster = true) }
        scope.launch(Dispatchers.IO) {
            try {
                val sales = localStorage.getAllSales(500)
                val (xlsx, _) = com.justplay.pos.services.ExcelReportExporter.exportMasterReport(
                    rooms = _uiState.value.rooms,
                    reservations = _uiState.value.reservations,
                    records = _uiState.value.sessionRecords,
                    sales = sales
                )
                _uiState.update {
                    it.copy(
                        masterExportNotification = "✅ Reporte General Completo exportado con éxito a:\n${xlsx.name}\nCarpeta: ${xlsx.parentFile?.absolutePath}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        masterExportNotification = "❌ Error al exportar Reporte General: ${e.message}"
                    )
                }
            } finally {
                _uiState.update { it.copy(isExportingMaster = false) }
            }
        }
    }

    fun dismissMasterExportNotification() {
        _uiState.update { it.copy(masterExportNotification = null) }
    }

    // ==========================================
    // GESTIÓN COMPLETA DE RESERVAS (DESKTOP)
    // ==========================================

    fun openNewReservationDialog(defaultCategory: String) {
        _uiState.update {
            it.copy(
                isReservationDialogOpen = true,
                editingReservation = null,
                defaultReservationCategory = defaultCategory
            )
        }
    }

    fun openEditReservationDialog(reservation: Reservation) {
        _uiState.update {
            it.copy(
                isReservationDialogOpen = true,
                editingReservation = reservation,
                defaultReservationCategory = reservation.tipoSala
            )
        }
    }

    fun closeReservationDialog() {
        _uiState.update {
            it.copy(
                isReservationDialogOpen = false,
                editingReservation = null
            )
        }
    }

    fun saveReservation(reservation: Reservation) {
        scope.launch {
            val ok = roomManagementService.saveReservation(reservation)
            if (ok) {
                _uiState.update {
                    it.copy(
                        isReservationDialogOpen = false,
                        editingReservation = null,
                        roomActionMessage = "✅ Reserva guardada con éxito (${reservation.nombre})"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(roomActionMessage = "⚠️ No se pudo guardar la reserva en Firebase")
                }
            }
        }
    }

    fun deleteReservation(reservationId: String) {
        scope.launch {
            val ok = roomManagementService.deleteReservation(reservationId)
            if (ok) {
                _uiState.update {
                    it.copy(roomActionMessage = "🗑️ Reserva eliminada")
                }
            }
        }
    }

    fun markReservationAttendance(reservation: Reservation) {
        val horaActual = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val updated = reservation.copy(
            asistio = true,
            horaLlegada = horaActual
        )
        scope.launch {
            val ok = roomManagementService.saveReservation(updated)
            if (ok) {
                _uiState.update {
                    it.copy(roomActionMessage = "🙋 Asistencia registrada a las $horaActual para ${reservation.nombre}")
                }
            }
        }
    }

    fun startAssigningReservation(reservation: Reservation) {
        val availableRooms = _uiState.value.rooms.filter {
            it.estado == com.justplay.pos.core.models.RoomState.DISPONIBLE && matchesRoomCategory(it, reservation.tipoSala)
        }

        if (availableRooms.isEmpty()) {
            _uiState.update {
                it.copy(roomActionMessage = "⚠️ No hay salas disponibles en este momento para '${reservation.tipoSala}'")
            }
            return
        }

        if (availableRooms.size == 1) {
            openRoomDetail(availableRooms.first(), prefill = reservation)
        } else {
            _uiState.update {
                it.copy(
                    isAssignReservationDialogOpen = true,
                    reservationToAssign = reservation
                )
            }
        }
    }

    fun closeAssignReservationDialog() {
        _uiState.update {
            it.copy(
                isAssignReservationDialogOpen = false,
                reservationToAssign = null
            )
        }
    }

    fun assignReservationToRoom(reservation: Reservation, room: Room) {
        closeAssignReservationDialog()
        openRoomDetail(room, prefill = reservation)
    }

    fun viewReservationReceipt(receiptUri: String?) {
        _uiState.update { it.copy(viewReceiptForReservation = receiptUri) }
    }

    fun closeReservationReceiptViewer() {
        _uiState.update { it.copy(viewReceiptForReservation = null) }
    }

    fun matchesRoomCategory(room: Room, targetCategory: String): Boolean {
        val cat = targetCategory.trim().lowercase()
        val roomCat = room.categoria.trim().lowercase()
        val roomSec = room.seccionNombre.trim().lowercase()
        return when {
            cat.contains("cine") || cat.contains("sala") -> roomCat == "minicine"
            cat.contains("aut") || cat.contains("volante") || cat.contains("car") || cat.contains("simulador") -> roomCat == "autitos"
            cat.contains("dance") -> roomCat == "just dance"
            else -> roomCat == cat || roomSec == cat || room.seccionId.equals(cat, ignoreCase = true)
        }
    }

    private fun filterProducts(all: List<Product>, category: String, query: String): List<Product> {
        val q = query.trim().lowercase()
        return all.filter { p ->
            val matchCat = category == "Todos" || p.category.equals(category, ignoreCase = true)
            val matchQuery = q.isEmpty() ||
                    p.name.lowercase().contains(q) ||
                    p.displayName.lowercase().contains(q) ||
                    p.barcode.contains(q) ||
                    p.gramaje.lowercase().contains(q)
            matchCat && matchQuery && p.active
        }
    }
}


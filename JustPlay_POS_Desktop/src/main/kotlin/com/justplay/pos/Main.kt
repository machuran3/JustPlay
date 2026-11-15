package com.justplay.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.justplay.pos.infrastructure.barcode.KeyboardBarcodeScanner
import com.justplay.pos.infrastructure.cloud.FirebaseRoomRepository
import com.justplay.pos.infrastructure.cloud.FirebaseSyncService
import com.justplay.pos.infrastructure.persistence.SqliteLocalStorage
import com.justplay.pos.infrastructure.security.DeviceSecurityService
import com.justplay.pos.services.ProductCatalogService
import com.justplay.pos.services.RoomManagementService
import com.justplay.pos.services.SaleService
import com.justplay.pos.ui.components.*
import com.justplay.pos.ui.state.PosViewModel
import com.justplay.pos.ui.theme.AccentGreen
import com.justplay.pos.ui.theme.BgDark
import com.justplay.pos.ui.theme.CardDark
import com.justplay.pos.ui.theme.JustPlayTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

fun main() = application {
    // -------------------------------------------------------------
    // INVERSIÓN DE DEPENDENCIAS / INYECCIÓN MANUAL (SOLID: DIP)
    // -------------------------------------------------------------
    val appScope = remember { CoroutineScope(Dispatchers.Default) }
    val deviceSecurityService = remember { DeviceSecurityService() }
    val localStorage = remember { SqliteLocalStorage() }
    val cloudSyncService = remember { FirebaseSyncService(securityService = deviceSecurityService) }
    val roomRepository = remember { FirebaseRoomRepository(securityService = deviceSecurityService) }
    val roomManagementService = remember { RoomManagementService(roomRepository, appScope) }
    val productCatalogService = remember { ProductCatalogService(localStorage, cloudSyncService) }
    val saleService = remember { SaleService(localStorage, cloudSyncService, productCatalogService, roomManagementService, appScope) }
    val barcodeScanner = remember { KeyboardBarcodeScanner() }

    val viewModel = remember {
        PosViewModel(
            barcodeScanner = barcodeScanner,
            productCatalogService = productCatalogService,
            saleService = saleService,
            roomManagementService = roomManagementService,
            localStorage = localStorage,
            cloudSyncService = cloudSyncService,
            securityService = deviceSecurityService,
            scope = appScope
        )
    }

    // Inicializar catálogo local y sincronización
    LaunchedEffect(Unit) {
        productCatalogService.initialize()
    }

    val state by viewModel.uiState.collectAsState()

    Window(
        onCloseRequest = {
            barcodeScanner.stopListening()
            exitApplication()
        },
        title = "🎮 JustPlay POS & Gestión de Salas (PC Windows)",
        state = WindowState(width = 1200.dp, height = 780.dp),
        onPreviewKeyEvent = { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                // Tecla Enter para Flujo Rápido de Cobro y Confirmación (solo si no viene de una pistola de escaneo)
                if (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) {
                    val now = System.currentTimeMillis()
                    if (now - barcodeScanner.lastScanTimestamp > 350L) {
                        if (state.isPaymentDialogOpen) {
                            viewModel.confirmCheckout()
                            return@Window true
                        }
                        // Si hay otro diálogo modal abierto, dejar que el diálogo procese su propio evento Enter
                        if (state.selectedRoomForDetail != null || state.isProductManagerOpen || state.isReservationDialogOpen || state.isAssignReservationDialogOpen) {
                            return@Window false
                        }
                        viewModel.onEnterKeyPressed()
                        return@Window true
                    }
                }
            }
            false
        }
    ) {
        JustPlayTheme {
            Scaffold(
                topBar = {
                    Column {
                        TopStatusBar(
                            state = state,
                            onSyncClick = { viewModel.triggerCloudSync() },
                            onHistoryClick = { viewModel.openHistoryDialog() },
                            onNewProductClick = { viewModel.openProductManager(null) },
                            onExportMasterClick = { viewModel.exportMasterReport() },
                            onDismissExportNotification = { viewModel.dismissMasterExportNotification() }
                        )

                        // Selector de Pestañas Principales (Minicine, Volantes, Just Dance, Tablero Reservas)
                        Surface(
                            color = CardDark,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isSalas = state.currentTab == "SALAS"
                                val countSalas = state.rooms.count { it.categoria == "Minicine" }
                                Button(
                                    onClick = { viewModel.switchTab("SALAS") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSalas) AccentGreen else BgDark,
                                        contentColor = if (isSalas) Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Text("🎬 Salas (Minicine) ($countSalas)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                val isVolantes = state.currentTab == "VOLANTES"
                                val countVolantes = state.rooms.count { it.categoria == "Autitos" }
                                Button(
                                    onClick = { viewModel.switchTab("VOLANTES") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isVolantes) AccentGreen else BgDark,
                                        contentColor = if (isVolantes) Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Text("🏎️ Volantes (Autitos) ($countVolantes)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                val isDance = state.currentTab == "JUST_DANCE"
                                val countDance = state.rooms.count { it.categoria == "Just Dance" }
                                Button(
                                    onClick = { viewModel.switchTab("JUST_DANCE") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDance) AccentGreen else BgDark,
                                        contentColor = if (isDance) Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Text("💃 Just Dance ($countDance)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                val dynamicCategories = remember(state.rooms) {
                                    state.rooms.map { it.categoria }
                                        .distinct()
                                        .filter { it.isNotBlank() && it !in listOf("Minicine", "Autitos", "Just Dance") }
                                }
                                for (customCat in dynamicCategories) {
                                    val isCustom = state.currentTab == "SEC_$customCat"
                                    val countCat = state.rooms.count { it.categoria.equals(customCat, ignoreCase = true) || it.seccionNombre.equals(customCat, ignoreCase = true) }
                                    Button(
                                        onClick = { viewModel.switchTab("SEC_$customCat") },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isCustom) AccentGreen else BgDark,
                                            contentColor = if (isCustom) Color.Black else Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(38.dp)
                                    ) {
                                        Text("🎮 $customCat ($countCat)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                val isSchedule = state.currentTab == "SCHEDULE"
                                Button(
                                    onClick = { viewModel.switchTab("SCHEDULE") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSchedule) AccentGreen else BgDark,
                                        contentColor = if (isSchedule) Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Text("📅 Tablero Reservas (${state.reservations.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                val isPos = state.currentTab == "POS"
                                Button(
                                    onClick = { viewModel.switchTab("POS") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPos) AccentGreen else BgDark,
                                        contentColor = if (isPos) Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Text("🛒 Venta Mostrador (POS)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgDark)
                        .padding(paddingValues)
                ) {
                    when (state.currentTab) {
                        "POS" -> {
                            // Vista 1: Punto de Venta con Carrito y Lector
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Panel Izquierdo (Lector y Catálogo de Productos)
                                Column(
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    ScannerStatusCard(
                                        state = state,
                                        onBarcodeTextChange = { viewModel.updateManualBarcodeText(it) },
                                        onSubmitManualBarcode = { viewModel.onManualBarcodeSubmit() }
                                    )

                                    ProductCatalogPanel(
                                        state = state,
                                        onSearchChange = { viewModel.onSearchQueryChanged(it) },
                                        onCategorySelect = { viewModel.onCategorySelected(it) },
                                        onProductClick = { viewModel.onProductCardClicked(it) },
                                        onProductEditClick = { viewModel.openProductManager(it) }
                                    )
                                }

                                // Panel Derecho (Tabla del Carrito de Venta y Cobro)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    CartTable(
                                        state = state,
                                        onQuantityChange = { barcode, q -> viewModel.onUpdateCartQuantity(barcode, q) },
                                        onRemoveItem = { barcode -> viewModel.onRemoveCartItem(barcode) },
                                        onClearCart = { viewModel.onClearCart() },
                                        onCheckoutClick = { viewModel.openPaymentDialog() }
                                    )
                                }
                            }
                        }
                        "SCHEDULE" -> {
                            ScheduleDashboardPanel(
                                rooms = state.rooms,
                                reservations = state.reservations,
                                records = state.sessionRecords,
                                selectedDate = state.scheduleSelectedDate,
                                categoryFilter = state.scheduleCategoryFilter,
                                viewMode = state.scheduleViewMode,
                                onDateChange = { viewModel.setScheduleSelectedDate(it) },
                                onCategoryFilterChange = { viewModel.setScheduleCategoryFilter(it) },
                                onViewModeChange = { viewModel.setScheduleViewMode(it) },
                                onRoomClick = { viewModel.openRoomDetail(it) },
                                onRefresh = { viewModel.refreshSchedule() }
                            )
                        }
                        "VOLANTES" -> {
                            val availableRooms = remember(state.rooms) {
                                state.rooms.filter { it.isAvailable && viewModel.matchesRoomCategory(it, "Autitos") }
                            }
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    RoomDashboardPanel(
                                        rooms = state.rooms,
                                        category = "Autitos",
                                        onRoomClick = { viewModel.openRoomDetail(it) },
                                        onRefreshRooms = { viewModel.refreshRooms() }
                                    )
                                }
                                RoomReservationsSidebar(
                                    category = "Autitos",
                                    reservations = state.reservations,
                                    availableRooms = availableRooms,
                                    onNewReservation = { viewModel.openNewReservationDialog("Autitos") },
                                    onEditReservation = { viewModel.openEditReservationDialog(it) },
                                    onDeleteReservation = { viewModel.deleteReservation(it) },
                                    onMarkAttendance = { viewModel.markReservationAttendance(it) },
                                    onPassToRoom = { viewModel.startAssigningReservation(it) },
                                    onViewReceipt = { viewModel.viewReservationReceipt(it) }
                                )
                            }
                        }
                        "JUST_DANCE" -> {
                            val availableRooms = remember(state.rooms) {
                                state.rooms.filter { it.isAvailable && viewModel.matchesRoomCategory(it, "Just Dance") }
                            }
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    RoomDashboardPanel(
                                        rooms = state.rooms,
                                        category = "Just Dance",
                                        onRoomClick = { viewModel.openRoomDetail(it) },
                                        onRefreshRooms = { viewModel.refreshRooms() }
                                    )
                                }
                                RoomReservationsSidebar(
                                    category = "Just Dance",
                                    reservations = state.reservations,
                                    availableRooms = availableRooms,
                                    onNewReservation = { viewModel.openNewReservationDialog("Just Dance") },
                                    onEditReservation = { viewModel.openEditReservationDialog(it) },
                                    onDeleteReservation = { viewModel.deleteReservation(it) },
                                    onMarkAttendance = { viewModel.markReservationAttendance(it) },
                                    onPassToRoom = { viewModel.startAssigningReservation(it) },
                                    onViewReceipt = { viewModel.viewReservationReceipt(it) }
                                )
                            }
                        }
                        "SALAS" -> {
                            val availableRooms = remember(state.rooms) {
                                state.rooms.filter { it.isAvailable && viewModel.matchesRoomCategory(it, "Minicine") }
                            }
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    RoomDashboardPanel(
                                        rooms = state.rooms,
                                        category = "Minicine",
                                        onRoomClick = { viewModel.openRoomDetail(it) },
                                        onRefreshRooms = { viewModel.refreshRooms() }
                                    )
                                }
                                RoomReservationsSidebar(
                                    category = "Minicine",
                                    reservations = state.reservations,
                                    availableRooms = availableRooms,
                                    onNewReservation = { viewModel.openNewReservationDialog("Minicine") },
                                    onEditReservation = { viewModel.openEditReservationDialog(it) },
                                    onDeleteReservation = { viewModel.deleteReservation(it) },
                                    onMarkAttendance = { viewModel.markReservationAttendance(it) },
                                    onPassToRoom = { viewModel.startAssigningReservation(it) },
                                    onViewReceipt = { viewModel.viewReservationReceipt(it) }
                                )
                            }
                        }
                        else -> {
                            val targetCategory = if (state.currentTab.startsWith("SEC_")) {
                                state.currentTab.removePrefix("SEC_")
                            } else {
                                "Minicine"
                            }
                            val availableRooms = remember(state.rooms, targetCategory) {
                                state.rooms.filter { it.isAvailable && viewModel.matchesRoomCategory(it, targetCategory) }
                            }
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    RoomDashboardPanel(
                                        rooms = state.rooms,
                                        category = targetCategory,
                                        onRoomClick = { viewModel.openRoomDetail(it) },
                                        onRefreshRooms = { viewModel.refreshRooms() }
                                    )
                                }
                                RoomReservationsSidebar(
                                    category = targetCategory,
                                    reservations = state.reservations,
                                    availableRooms = availableRooms,
                                    onNewReservation = { viewModel.openNewReservationDialog(targetCategory) },
                                    onEditReservation = { viewModel.openEditReservationDialog(it) },
                                    onDeleteReservation = { viewModel.deleteReservation(it) },
                                    onMarkAttendance = { viewModel.markReservationAttendance(it) },
                                    onPassToRoom = { viewModel.startAssigningReservation(it) },
                                    onViewReceipt = { viewModel.viewReservationReceipt(it) }
                                )
                            }
                        }
                    }
                }

                // Diálogos Modales
                if (state.isPaymentDialogOpen) {
                    PaymentDialog(
                        state = state,
                        onPaymentMethodChange = { viewModel.onPaymentMethodChanged(it) },
                        onCashReceivedChange = { viewModel.onCashReceivedChanged(it) },
                        onClientNameChange = { viewModel.onClientNameChanged(it) },
                        onClientPhoneChange = { viewModel.onClientPhoneChanged(it) },
                        onDestinationTypeChange = { viewModel.setSaleDestination(it) },
                        onTargetRoomSelected = { id, name -> viewModel.setTargetRoom(id, name) },
                        onRequestQrValidation = { viewModel.requestQrValidationFromMobile() },
                        onCancelQrValidation = { viewModel.cancelQrValidation() },
                        onPickReceiptPhotos = { viewModel.pickAndAttachSalePhotos() },
                        onUpdateReceiptPhotos = { viewModel.updateSaleReceiptUrl(it) },
                        onConfirmCheckout = { viewModel.confirmCheckout() },
                        onDismiss = { viewModel.closePaymentDialog() }
                    )
                }

                if (state.selectedRoomForDetail != null) {
                    RoomDetailDialog(
                        room = state.selectedRoomForDetail!!,
                        prefillReservation = state.prefillReservationForRoom,
                        pendingOrders = state.roomPendingOrders,
                        catalogProducts = state.productsCatalog,
                        lastScannedBarcode = state.lastScannedBarcode ?: "",
                        isWaitingQrValidation = state.isWaitingRoomQrValidation,
                        qrValidationStatusMessage = state.roomQrValidationStatusMessage,
                        verifiedReceiptUrl = state.roomQrComprobanteUrl,
                        onRequestQrValidation = { client, phone, movie, dur, price, method, efec, qr, ords ->
                            viewModel.requestRoomQrValidation(
                                room = state.selectedRoomForDetail!!,
                                client = client,
                                phone = phone,
                                gameOrMovie = movie,
                                durationMin = dur,
                                totalAmount = price + ords.sumOf { it.subtotal },
                                montoEfectivo = efec,
                                montoQR = qr,
                                metodoPago = method,
                                pedidos = ords
                            )
                        },
                        onCancelQrValidation = { viewModel.cancelRoomQrValidation() },
                        onPickReceiptPhotos = { viewModel.pickAndAttachRoomPhotos() },
                        onUpdateReceiptPhotos = { viewModel.updateRoomReceiptUrl(it) },
                        onPickActiveRoomPhotos = { roomId -> viewModel.pickAndAttachPhotosToActiveRoom(roomId) },
                        onUpdateActiveRoomPhotos = { roomId, newStr -> viewModel.updateActiveRoomComprobante(roomId, newStr) },
                        onAddProduct = { viewModel.addProductToOpenRoom(it, 1) },
                        onRemoveProduct = { viewModel.removeProductFromOpenRoom(it) },
                        onAddTime = { viewModel.confirmAddTime(state.selectedRoomForDetail!!.id, it) },
                        onClearRoom = { viewModel.clearRoom(state.selectedRoomForDetail!!) },
                        onConfirmStart = { client, phone, gameOrMovie, duration, price, method, pedidos, receipt ->
                            viewModel.confirmStartRoom(
                                client = client,
                                phone = phone,
                                gameOrMovie = gameOrMovie,
                                durationMin = duration,
                                price = price,
                                paymentMethod = method,
                                pedidos = pedidos,
                                receiptImageUrl = receipt,
                                associatedReservationId = state.prefillReservationForRoom?.idFirebase
                            )
                        },
                        onUpdateActiveRoomSession = { client, phone, movie, price, extraMins, method ->
                            viewModel.updateActiveRoomSession(state.selectedRoomForDetail!!.id, client, phone, movie, price, extraMins, method)
                        },
                        isActionInProgress = state.isRoomActionInProgress,
                        onDismiss = { viewModel.closeRoomDetail() }
                    )
                }

                if (state.isProductManagerOpen) {
                    ProductManagerDialog(
                        initialProduct = state.selectedProductForEdit,
                        prefillBarcode = state.manualBarcodeText,
                        existingProducts = state.productsCatalog,
                        onSaveProduct = { prod, oldCode -> viewModel.saveProduct(prod, oldCode) },
                        onDeleteProduct = { viewModel.deleteProduct(it) },
                        onDismiss = { viewModel.closeProductManager() }
                    )
                }

                if (state.isHistoryDialogOpen) {
                    HistoryDialog(
                        sales = state.recentSales,
                        onDismiss = { viewModel.closeHistoryDialog() }
                    )
                }

                // Diálogo de Registro y Edición de Reservas (Estilo Móvil)
                if (state.isReservationDialogOpen) {
                    ReservationFormDialog(
                        initialReservation = state.editingReservation,
                        defaultCategory = state.defaultReservationCategory,
                        availableCategories = remember(state.rooms) {
                            state.rooms.map { it.categoria }.distinct().filter { it.isNotBlank() }
                        },
                        onSaveReservation = { viewModel.saveReservation(it) },
                        onDismiss = { viewModel.closeReservationDialog() }
                    )
                }

                // Diálogo de Asignación / Pasar Reserva a Sala Disponible
                if (state.isAssignReservationDialogOpen && state.reservationToAssign != null) {
                    val targetRes = state.reservationToAssign!!
                    val availableRooms = remember(state.rooms, targetRes) {
                        state.rooms.filter {
                            it.isAvailable && viewModel.matchesRoomCategory(it, targetRes.tipoSala)
                        }
                    }
                    AssignRoomDialog(
                        reservation = targetRes,
                        availableRooms = availableRooms,
                        onSelectRoom = { room -> viewModel.assignReservationToRoom(targetRes, room) },
                        onDismiss = { viewModel.closeAssignReservationDialog() }
                    )
                }

                // Diálogo para visualización de comprobante de reserva
                if (!state.viewReceiptForReservation.isNullOrBlank()) {
                    ReceiptViewerDialog(
                        receiptString = state.viewReceiptForReservation!!,
                        title = "Comprobante de Reserva",
                        onDismiss = { viewModel.closeReservationReceiptViewer() }
                    )
                }

                // DIÁLOGOS DE SEGURIDAD PERSISTENTES Y BLOQUEO DURO (SECURITY GUARDS)
                if (state.isDeviceRevoked) {
                    DeviceRevokedDialog(
                        deviceId = state.deviceId,
                        onRetryCheck = { viewModel.checkDeviceAuthorization() }
                    )
                }

                if (state.isVersionObsolete) {
                    ForceUpdateDialog(
                        currentVersion = state.currentAppVersion,
                        minRequiredVersion = state.minRequiredVersion
                    )
                }
            }
        }
    }
}

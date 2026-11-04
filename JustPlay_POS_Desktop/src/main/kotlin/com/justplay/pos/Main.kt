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
                if (keyEvent.key == Key.Enter) {
                    val now = System.currentTimeMillis()
                    if (now - barcodeScanner.lastScanTimestamp > 350L) {
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
                            RoomDashboardPanel(
                                rooms = state.rooms,
                                category = "Autitos",
                                onRoomClick = { viewModel.openRoomDetail(it) },
                                onRefreshRooms = { viewModel.refreshRooms() }
                            )
                        }
                        "JUST_DANCE" -> {
                            RoomDashboardPanel(
                                rooms = state.rooms,
                                category = "Just Dance",
                                onRoomClick = { viewModel.openRoomDetail(it) },
                                onRefreshRooms = { viewModel.refreshRooms() }
                            )
                        }
                        else -> { // "SALAS" (Minicine)
                            RoomDashboardPanel(
                                rooms = state.rooms,
                                category = "Minicine",
                                onRoomClick = { viewModel.openRoomDetail(it) },
                                onRefreshRooms = { viewModel.refreshRooms() }
                            )
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
                        onConfirmCheckout = { viewModel.confirmCheckout() },
                        onDismiss = { viewModel.closePaymentDialog() }
                    )
                }

                if (state.selectedRoomForDetail != null) {
                    RoomDetailDialog(
                        room = state.selectedRoomForDetail!!,
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
                        onAddProduct = { viewModel.addProductToOpenRoom(it, 1) },
                        onRemoveProduct = { viewModel.removeProductFromOpenRoom(it) },
                        onAddTime = { viewModel.confirmAddTime(state.selectedRoomForDetail!!.id, it) },
                        onClearRoom = { viewModel.clearRoom(state.selectedRoomForDetail!!) },
                        onConfirmStart = { client, phone, gameOrMovie, duration, price, method, pedidos, receipt ->
                            viewModel.confirmStartRoom(client, phone, gameOrMovie, duration, price, method, pedidos, receipt)
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

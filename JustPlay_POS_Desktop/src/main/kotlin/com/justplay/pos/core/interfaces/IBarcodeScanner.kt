package com.justplay.pos.core.interfaces

import kotlinx.coroutines.flow.SharedFlow

/**
 * Interface Segregation: Abstracción para receptores de códigos de barras (HID, Serial, etc.).
 */
interface IBarcodeScanner {
    val barcodeScannedFlow: SharedFlow<String>
    fun startListening()
    fun stopListening()
    fun simulateBarcodeScan(barcode: String)
}

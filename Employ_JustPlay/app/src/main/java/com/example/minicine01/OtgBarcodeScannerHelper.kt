package com.example.minicine01

import android.view.KeyEvent

/**
 * Interface Segregation Principle (ISP):
 * Contrato específico para la escucha de eventos de escaneo de código de barras.
 */
interface IBarcodeScanListener {
    fun onBarcodeScanned(barcode: String)
}

/**
 * Single Responsibility Principle (SRP):
 * Intercepta y procesa ráfagas rápidas de KeyEvent emitidas por pistolas lectoras USB-OTG / Bluetooth HID
 * sin interferir con la interacción normal del usuario.
 */
class OtgBarcodeScannerHelper(
    private val listener: IBarcodeScanListener,
    private val maxCharacterIntervalMs: Long = 75L
) {
    private val buffer = StringBuilder()
    private var lastKeyTimestamp = 0L

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val currentTime = System.currentTimeMillis()
        val interval = currentTime - lastKeyTimestamp

        if (interval > maxCharacterIntervalMs && buffer.isNotEmpty()) {
            buffer.clear()
        }
        lastKeyTimestamp = currentTime

        val unicodeChar = event.unicodeChar
        if (unicodeChar > 0 && unicodeChar != 10 && unicodeChar != 13) {
            val char = unicodeChar.toChar()
            if (char.isLetterOrDigit() || char in "-_./") {
                buffer.append(char)
            }
        }

        if (event.keyCode == KeyEvent.KEYCODE_ENTER || unicodeChar == 10 || unicodeChar == 13) {
            val barcode = buffer.toString().trim()
            buffer.clear()
            if (barcode.length >= 3) {
                listener.onBarcodeScanned(barcode)
                return true
            }
        }

        return false
    }
}

package com.example.adminjustplay

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
 * sin interferir con la escritura normal del teclado táctil o físico humano.
 */
class OtgBarcodeScannerHelper(
    private val listener: IBarcodeScanListener,
    private val maxCharacterIntervalMs: Long = 75L
) {
    private val buffer = StringBuilder()
    private var lastKeyTimestamp = 0L

    /**
     * Procesa el evento de teclado.
     * @return true si el evento fue consumido por el escáner de código de barras.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val currentTime = System.currentTimeMillis()
        val interval = currentTime - lastKeyTimestamp

        // Si el tiempo entre teclas supera el umbral, se reinicia el buffer (es entrada manual humana)
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

        // Al presionar Enter, si el buffer tiene un tamaño razonable de código de barras, disparamos el evento
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

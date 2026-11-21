package com.justplay.pos.infrastructure.barcode

import com.justplay.pos.core.interfaces.IBarcodeScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/**
 * Capturador global de pistolas lectoras de códigos de barras (HID Keyboard Emulation).
 * 
 * Diseñado para capturar con 100% de efectividad desde el PRIMER escaneo (sin perder el primer dígito ni retardos).
 */
class KeyboardBarcodeScanner(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val maxCharIntervalMs: Long = 120L
) : IBarcodeScanner, KeyEventDispatcher {

    private val _barcodeScannedFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val barcodeScannedFlow: SharedFlow<String> = _barcodeScannedFlow.asSharedFlow()

    private val buffer = StringBuilder()
    private var lastKeyTimestamp = 0L
    private var lastEnterTimestamp = 0L
    var lastScanTimestamp = 0L
        private set
    private var isListening = false

    override fun startListening() {
        if (!isListening) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this)
            isListening = true
        }
    }

    override fun stopListening() {
        if (isListening) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this)
            isListening = false
            buffer.clear()
            lastKeyTimestamp = 0L
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (!isListening) return false

        val currentTime = System.currentTimeMillis()

        // 1. Manejo de fin de código de barras (ENTER)
        val isEnter = (e.id == KeyEvent.KEY_PRESSED && (e.keyCode == KeyEvent.VK_ENTER)) ||
                      (e.id == KeyEvent.KEY_TYPED && (e.keyChar == '\n' || e.keyChar == '\r'))

        if (isEnter) {
            val hadBuffer = buffer.length >= 3
            val isRecentBurst = (currentTime - lastKeyTimestamp <= 130L)

            if (currentTime - lastEnterTimestamp < 100L) {
                return hadBuffer && isRecentBurst
            }
            lastEnterTimestamp = currentTime

            if (hadBuffer && isRecentBurst) {
                val scannedCode = buffer.toString().trim()
                buffer.clear()
                lastScanTimestamp = currentTime
                lastKeyTimestamp = 0L
                scope.launch {
                    _barcodeScannedFlow.emit(scannedCode)
                }
                // Consumimos el Enter para que no active botones ni saltos de línea
                return true
            }
            buffer.clear()
            lastKeyTimestamp = 0L
            return false
        }

        // 2. Acumular caracteres alfanuméricos y consumir ráfagas de escaneo rápido
        if (e.id == KeyEvent.KEY_TYPED) {
            val char = e.keyChar
            if (char.isLetterOrDigit() || char in "-_.+") {
                val timeSinceLastKey = if (lastKeyTimestamp == 0L) Long.MAX_VALUE else (currentTime - lastKeyTimestamp)
                lastKeyTimestamp = currentTime

                // Si pasaron más de maxCharIntervalMs y ya había caracteres acumulados, reiniciamos el buffer con el nuevo caracter
                if (timeSinceLastKey > maxCharIntervalMs && buffer.isNotEmpty()) {
                    buffer.clear()
                }
                buffer.append(char)

                // Si la tecla llegó a velocidad de lector de código de barras (< 120ms) o ya tenemos acumulación rápida,
                // CONSUMIMOS el evento para que NO se escriba en ningún TextField activo
                if (timeSinceLastKey <= 120L || (buffer.length >= 2 && timeSinceLastKey <= 150L)) {
                    return true
                }
            }
        } else if (e.id == KeyEvent.KEY_PRESSED) {
            val timeSinceLastKey = if (lastKeyTimestamp == 0L) Long.MAX_VALUE else (currentTime - lastKeyTimestamp)
            if (timeSinceLastKey <= 120L && buffer.isNotEmpty()) {
                return true
            }
            if (lastKeyTimestamp != 0L && currentTime - lastKeyTimestamp > maxCharIntervalMs * 2) {
                buffer.clear()
                lastKeyTimestamp = 0L
            }
        } else if (e.id == KeyEvent.KEY_RELEASED) {
            val timeSinceLastKey = if (lastKeyTimestamp == 0L) Long.MAX_VALUE else (currentTime - lastKeyTimestamp)
            if (timeSinceLastKey <= 120L && buffer.isNotEmpty()) {
                return true
            }
        }

        return false
    }

    override fun simulateBarcodeScan(barcode: String) {
        val clean = barcode.trim()
        if (clean.isNotEmpty()) {
            lastScanTimestamp = System.currentTimeMillis()
            scope.launch {
                _barcodeScannedFlow.emit(clean)
            }
        }
    }
}

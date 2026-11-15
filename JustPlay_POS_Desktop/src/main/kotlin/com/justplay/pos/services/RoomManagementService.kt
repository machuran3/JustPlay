package com.justplay.pos.services

import com.justplay.pos.core.interfaces.IRoomRepository
import com.justplay.pos.core.models.Reservation
import com.justplay.pos.core.models.Room
import com.justplay.pos.core.models.RoomOrder
import com.justplay.pos.core.models.SessionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single Responsibility Principle (SRP):
 * Casos de uso de gestión de salas: monitoreo en tiempo real, inicio, extensión de tiempo y liberación.
 */
class RoomManagementService(
    private val roomRepository: IRoomRepository,
    private val coroutineScope: CoroutineScope
) {
    val roomsFlow: StateFlow<List<Room>> = roomRepository.roomsFlow
    val reservationsFlow: StateFlow<List<Reservation>> = roomRepository.reservationsFlow
    val recordsFlow: StateFlow<List<SessionRecord>> = roomRepository.recordsFlow

    init {
        startAutoRefreshLoop()
    }

    private fun startAutoRefreshLoop() {
        coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    roomRepository.fetchRooms()
                    roomRepository.fetchReservations()
                    roomRepository.fetchRecords()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(3000) // Refrescar estado de salas, reservas y registros cada 3 segundos
            }
        }
    }

    suspend fun refreshRooms(): List<Room> {
        roomRepository.fetchReservations()
        roomRepository.fetchRecords()
        return roomRepository.fetchRooms()
    }

    suspend fun startRoom(
        roomId: Int,
        clientName: String,
        clientPhone: String,
        gameOrMovie: String,
        durationMinutes: Int,
        price: Double,
        paymentMethod: String,
        pedidos: List<RoomOrder> = emptyList(),
        receiptImageUrl: String? = null
    ): Boolean {
        return roomRepository.startRoom(
            roomId = roomId,
            clientName = clientName,
            clientPhone = clientPhone,
            gameOrMovie = gameOrMovie,
            durationMinutes = durationMinutes,
            price = price,
            paymentMethod = paymentMethod,
            pedidos = pedidos,
            receiptImageUrl = receiptImageUrl
        )
    }

    suspend fun addTimeToRoom(roomId: Int, extraMinutes: Int): Boolean {
        return roomRepository.addTimeToRoom(roomId, extraMinutes)
    }

    suspend fun clearRoom(roomId: Int): Boolean {
        return roomRepository.clearRoom(roomId)
    }

    suspend fun addOrderToRoom(roomId: Int, order: RoomOrder): Boolean {
        return roomRepository.addOrderToRoom(roomId, order)
    }

    suspend fun removeOrderFromRoom(roomId: Int, orderIndex: Int): Boolean {
        return roomRepository.removeOrderFromRoom(roomId, orderIndex)
    }

    suspend fun updateRoomSession(
        roomId: Int,
        clientName: String,
        clientPhone: String,
        gameOrMovie: String,
        price: Double,
        extraDurationMinutes: Int? = null,
        newPaymentMethod: String? = null
    ): Boolean {
        val currentRooms = roomRepository.fetchRooms()
        val existing = currentRooms.find { it.id == roomId } ?: return false

        var newEndTime = existing.endTimeMillis
        var newHoraFin = existing.horaFinProgramada
        if (extraDurationMinutes != null && extraDurationMinutes != 0) {
            val baseTime = existing.endTimeMillis ?: System.currentTimeMillis()
            newEndTime = baseTime + (extraDurationMinutes * 60 * 1000L)
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = newEndTime
            newHoraFin = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(cal.time)
        }

        val updated = existing.copy(
            cliente = clientName.trim(),
            celular = clientPhone.trim(),
            pelicula = gameOrMovie.trim(),
            precioCobrado = price,
            endTimeMillis = newEndTime,
            horaFinProgramada = newHoraFin,
            metodoPago = newPaymentMethod ?: existing.metodoPago
        )
        val roomUpdated = roomRepository.updateRoom(updated)
        if (roomUpdated) {
            // Sincronizar inmediatamente a /registros y /auditoria_registros para que refleje el nuevo precio
            roomRepository.updateRoomRecordSession(updated)
        }
        return roomUpdated
    }

    suspend fun updateRoomComprobante(roomId: Int, comprobanteUri: String?): Boolean {
        val currentRooms = roomRepository.fetchRooms()
        val existing = currentRooms.find { it.id == roomId } ?: return false
        val updated = existing.copy(comprobanteUri = comprobanteUri)
        return roomRepository.updateRoom(updated)
    }

    suspend fun saveReservation(reservation: Reservation): Boolean {
        return roomRepository.saveReservation(reservation)
    }

    suspend fun deleteReservation(reservationId: String): Boolean {
        return roomRepository.deleteReservation(reservationId)
    }
}



package com.justplay.pos.core.interfaces

import com.justplay.pos.core.models.Reservation
import com.justplay.pos.core.models.Room
import com.justplay.pos.core.models.RoomOrder
import com.justplay.pos.core.models.SessionRecord
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface Segregation Principle (ISP):
 * Contrato específico para la consulta, modificación y auditoría de salas.
 */
interface IRoomRepository {
    val roomsFlow: StateFlow<List<Room>>
    val reservationsFlow: StateFlow<List<Reservation>>
    val recordsFlow: StateFlow<List<SessionRecord>>

    suspend fun fetchRooms(): List<Room>
    suspend fun fetchReservations(): List<Reservation>
    suspend fun fetchRecords(): List<SessionRecord>
    suspend fun updateRoom(room: Room): Boolean
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
    ): Boolean
    suspend fun addTimeToRoom(roomId: Int, extraMinutes: Int): Boolean
    suspend fun clearRoom(roomId: Int): Boolean
    suspend fun addOrderToRoom(roomId: Int, order: RoomOrder, shouldRegisterInVentas: Boolean = true): Boolean
    suspend fun removeOrderFromRoom(roomId: Int, orderIndex: Int): Boolean
    suspend fun updateRoomRecordSession(room: Room): Boolean
    suspend fun saveReservation(reservation: Reservation): Boolean
    suspend fun deleteReservation(reservationId: String): Boolean
    suspend fun logRoomAudit(
        actionType: String,
        roomId: String,
        roomName: String,
        category: String,
        durationMinutes: Int = 0,
        details: String = ""
    )
}

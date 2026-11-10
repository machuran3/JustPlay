package com.justplay.pos.services

import com.justplay.pos.core.models.Reservation
import com.justplay.pos.core.models.Room
import com.justplay.pos.core.models.RoomState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReservationScheduleAllocatorTest {

    private val minicineRooms = (1..10).map { i ->
        Room(
            id = i,
            nombre = "Sala $i",
            estado = RoomState.DISPONIBLE
        )
    }

    private val autitosRooms = (1..8).map { i ->
        Room(
            id = 100 + i,
            nombre = "Car $i",
            estado = RoomState.DISPONIBLE
        )
    }

    @Test
    fun testMinicineConsecutiveReservationsDistributeToSeparateRoomsWithTurnaroundBuffer() {
        val natalio = Reservation(
            idFirebase = "res_natalio",
            nombre = "Natalio",
            tipoSala = "Minicine",
            fecha = "10/11/2026",
            horaProgramada = "17:30",
            duracion = "1:00" // 17:30 - 18:30
        )
        val cecilia = Reservation(
            idFirebase = "res_cecilia",
            nombre = "Cecilia",
            tipoSala = "Minicine",
            fecha = "10/11/2026",
            horaProgramada = "18:30",
            duracion = "1:00" // 18:30 - 19:30
        )

        val assignments = ReservationScheduleAllocator.computeAssignments(
            rooms = minicineRooms,
            reservations = listOf(natalio, cecilia),
            records = emptyList(),
            isToday = false
        )

        assertEquals(listOf(1), assignments["res_natalio"])
        assertEquals(listOf(2), assignments["res_cecilia"])
    }

    @Test
    fun testMinicineNonOverlappingGoesToSala1() {
        val resMorning = Reservation(
            idFirebase = "res_morning",
            nombre = "Carlos",
            tipoSala = "Minicine",
            fecha = "10/11/2026",
            horaProgramada = "11:00",
            duracion = "1:00"
        )
        val resEvening = Reservation(
            idFirebase = "res_evening",
            nombre = "Ana",
            tipoSala = "Minicine",
            fecha = "10/11/2026",
            horaProgramada = "19:00",
            duracion = "1:00"
        )

        val assignments = ReservationScheduleAllocator.computeAssignments(
            rooms = minicineRooms,
            reservations = listOf(resMorning, resEvening),
            records = emptyList(),
            isToday = false
        )

        assertEquals(listOf(1), assignments["res_morning"])
        assertEquals(listOf(1), assignments["res_evening"])
    }

    @Test
    fun testAutitosMultipleWheelsAllocation() {
        val resCarGroup = Reservation(
            idFirebase = "res_group",
            nombre = "Familia Gomez",
            tipoSala = "Autitos",
            fecha = "10/11/2026",
            horaProgramada = "16:00",
            duracion = "1:00",
            cantidadVolantes = 3
        )
        val resSingleCar = Reservation(
            idFirebase = "res_single",
            nombre = "Lucas",
            tipoSala = "Autitos",
            fecha = "10/11/2026",
            horaProgramada = "16:00",
            duracion = "1:00",
            cantidadVolantes = 1
        )

        val assignments = ReservationScheduleAllocator.computeAssignments(
            rooms = autitosRooms,
            reservations = listOf(resCarGroup, resSingleCar),
            records = emptyList(),
            isToday = false
        )

        assertEquals(listOf(101, 102, 103), assignments["res_group"])
        assertEquals(listOf(104), assignments["res_single"])
    }

    @Test
    fun testExplicitRoomAssignmentRespected() {
        val explicitRes = Reservation(
            idFirebase = "res_sala5",
            nombre = "Grupo VIP",
            tipoSala = "Sala 5",
            fecha = "10/11/2026",
            horaProgramada = "18:00",
            duracion = "1:00"
        )
        val generalRes = Reservation(
            idFirebase = "res_general",
            nombre = "Cliente Regular",
            tipoSala = "Minicine",
            fecha = "10/11/2026",
            horaProgramada = "18:00",
            duracion = "1:00"
        )

        val assignments = ReservationScheduleAllocator.computeAssignments(
            rooms = minicineRooms,
            reservations = listOf(explicitRes, generalRes),
            records = emptyList(),
            isToday = false
        )

        assertEquals(listOf(5), assignments["res_sala5"])
        assertEquals(listOf(1), assignments["res_general"])
    }
}

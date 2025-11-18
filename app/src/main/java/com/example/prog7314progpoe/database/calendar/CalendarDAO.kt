package com.example.prog7314progpoe.database.calendar

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CalendarDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(calendar: CalendarModel)

    @Query("SELECT * FROM calendars WHERE calendarId = :id")
    suspend fun getCalendarById(id: String): CalendarModel?

    @Query("SELECT * FROM calendars")
    suspend fun getAllCalendars(): List<CalendarModel>

    @Delete
    suspend fun delete(calendar: CalendarModel)

    @Update
    suspend fun update(calendar: CalendarModel)

    @Query("DELETE FROM calendars")
    suspend fun deleteAll()

    // **NEW: Get calendars for specific user**
    @Query("SELECT * FROM calendars WHERE ownerId = :userId")
    suspend fun getCalendarsByUser(userId: String): List<CalendarModel>

    // **NEW: Delete calendars for specific user**
    @Query("DELETE FROM calendars WHERE ownerId = :userId")
    suspend fun deleteCalendarsByUser(userId: String)

    // **NEW: Check if calendar exists**
    @Query("SELECT COUNT(*) FROM calendars WHERE calendarId = :calendarId")
    suspend fun calendarExists(calendarId: String): Int
}
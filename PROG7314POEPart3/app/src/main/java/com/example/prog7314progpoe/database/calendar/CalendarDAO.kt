package com.example.prog7314progpoe.database.calendar

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * DAO (Data Access Object) for CalendarModel.
 * Provides methods for inserting, updating, querying, and deleting calendars in Room.
 */
@Dao
interface CalendarDAO {

    // -------------------------
    // INSERT
    // -------------------------
    /** Insert a calendar. Replace if it already exists (by primary key). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(calendar: CalendarModel)

    // -------------------------
    // QUERY / READ
    // -------------------------
    /** Get a single calendar by its ID. Returns null if not found. */
    @Query("SELECT * FROM calendars WHERE calendarId = :id")
    suspend fun getCalendarById(id: String): CalendarModel?

    /** Get all calendars in the database. */
    @Query("SELECT * FROM calendars")
    suspend fun getAllCalendars(): List<CalendarModel>

    /** Get all calendars owned by a specific user. */
    @Query("SELECT * FROM calendars WHERE ownerId = :userId")
    suspend fun getCalendarsByUser(userId: String): List<CalendarModel>

    /** Check if a calendar exists by counting matching IDs. Returns count (0 or 1). */
    @Query("SELECT COUNT(*) FROM calendars WHERE calendarId = :calendarId")
    suspend fun calendarExists(calendarId: String): Int

    // -------------------------
    // UPDATE
    // -------------------------
    /** Update an existing calendar. */
    @Update
    suspend fun update(calendar: CalendarModel)

    // -------------------------
    // DELETE
    // -------------------------
    /** Delete a specific calendar. */
    @Delete
    suspend fun delete(calendar: CalendarModel)

    /** Delete all calendars in the database. */
    @Query("DELETE FROM calendars")
    suspend fun deleteAll()

    /** Delete all calendars owned by a specific user. */
    @Query("DELETE FROM calendars WHERE ownerId = :userId")
    suspend fun deleteCalendarsByUser(userId: String)
}

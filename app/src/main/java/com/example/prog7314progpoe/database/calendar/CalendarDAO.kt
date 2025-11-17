package com.example.prog7314progpoe.database.calendar

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface CalendarDAO {
    @Insert
    fun insert(calendarModel: CalendarModel): String

    @Query("SELECT * FROM calendars")
    fun getAllCalendars(): List<CalendarModel>

    @Delete
    fun delete(calendarModel: CalendarModel): String

    @Update
    fun update(calendarModel: CalendarModel): String

}
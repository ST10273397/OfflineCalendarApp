package com.example.prog7314progpoe.database.holidays

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface HolidayDAO {

    @Insert
    fun insert(holidayModel: HolidayModel): String

    @Query("SELECT * FROM holidays")
    fun getAllHolidays(): List<HolidayModel>

    @Delete
    fun delete(holidayModel: HolidayModel): String

    @Update
    fun update(holidayModel: HolidayModel): String
}
package com.example.prog7314progpoe.database.user

import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.prog7314progpoe.database.calendar.CalendarModel

@Entity
interface UserDAO {

    @Insert
    fun insert(userModel: UserModel): String

    @Query("SELECT * FROM Users")
    fun getAllOUsers(): List<UserModel>

    @Delete
    fun delete(calendarModel: CalendarModel): String

    @Update
    fun update(calendarModel: CalendarModel): String
}
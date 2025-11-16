package com.example.prog7314progpoe.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.prog7314progpoe.database.calendar.CalendarDAO
import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.example.prog7314progpoe.database.holidays.HolidayDAO
import com.example.prog7314progpoe.database.user.UserDAO
import com.example.prog7314progpoe.database.user.UserModel

@Database(entities =[CalendarModel::class,
                    HolidayModel::class,
                    UserModel::class],
                    version = 1)
abstract class AppDatabase: RoomDatabase() {
    abstract fun calendarDAO(): CalendarDAO
    abstract fun holidayDAO(): HolidayDAO
    abstract fun UserDAO(): UserDAO

    companion object{
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getCalendarDatabase(context: Context): Any {
            return INSTANCE?: synchronized(this){
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calendar_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }

        fun getHolidayDatabase(context: Context): Any {
            return INSTANCE?: synchronized(this){
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "holiday_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }

        fun getUserDatabase(context: Context): Any {
            return INSTANCE?: synchronized(this){
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "user_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
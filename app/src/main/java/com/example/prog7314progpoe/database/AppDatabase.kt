package com.example.prog7314progpoe.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.prog7314progpoe.database.calendar.CalendarDAO
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.example.prog7314progpoe.database.Converters
import com.example.prog7314progpoe.database.holidays.HolidayDAO
import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.example.prog7314progpoe.database.user.UserDAO
import com.example.prog7314progpoe.database.user.UserModel

@Database(
    entities = [
        CalendarModel::class,
        HolidayModel::class,
        UserModel::class
    ],
    version = 6, // Increment version for schema changes
    exportSchema = false
)
@TypeConverters(Converters::class) // Add TypeConverters
abstract class AppDatabase : RoomDatabase() {
    abstract fun calendarDAO(): CalendarDAO
    abstract fun holidayDAO(): HolidayDAO
    abstract fun userDAO(): UserDAO

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getCalendarDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chronosync_database" // Single unified database
                )
                    .fallbackToDestructiveMigration() // For development - handle migrations properly in production
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Alias methods for compatibility
        fun getHolidayDatabase(context: Context): AppDatabase = getCalendarDatabase(context)
        fun getUserDatabase(context: Context): AppDatabase = getCalendarDatabase(context)
    }
}
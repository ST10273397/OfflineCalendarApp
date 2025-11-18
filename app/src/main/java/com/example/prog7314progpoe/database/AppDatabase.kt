package com.example.prog7314progpoe.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.prog7314progpoe.database.calendar.CalendarDAO
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.example.prog7314progpoe.database.holidays.HolidayDAO
import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.example.prog7314progpoe.database.user.UserDAO
import com.example.prog7314progpoe.database.user.UserModel

/**
 * Main Room database for the Chronosync app.
 * Stores Calendar, Holiday, and User entities.
 * Includes TypeConverters for complex data types.
 */
@Database(
    entities = [
        CalendarModel::class,
        HolidayModel::class,
        UserModel::class
    ],
    version = 8,                 // Increment this for schema changes
    exportSchema = false         // Disable schema export for simplicity
)
@TypeConverters(Converters::class) // Handles maps, lists, and embedded objects
abstract class AppDatabase : RoomDatabase() {

    // -------------------------------
    // DAO Accessors
    // -------------------------------

    abstract fun calendarDAO(): CalendarDAO
    abstract fun holidayDAO(): HolidayDAO
    abstract fun userDAO(): UserDAO

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton instance of AppDatabase.
         * Ensures only one instance is created across threads.
         */
        fun getCalendarDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chronosync_database" // Unified database for all entities
                )
                    .fallbackToDestructiveMigration() // WARNING: Deletes data on version change; for dev only
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Aliases for backward compatibility / clarity.
         * All methods return the same unified database instance.
         */
        fun getHolidayDatabase(context: Context): AppDatabase = getCalendarDatabase(context)
        fun getUserDatabase(context: Context): AppDatabase = getCalendarDatabase(context)
    }
}

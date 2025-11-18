package com.example.prog7314progpoe.database.calendar

import android.content.Context
import android.util.Log
import com.example.prog7314progpoe.database.AppDatabase
import com.example.prog7314progpoe.database.holidays.FirebaseHolidayDbHelper
import com.example.prog7314progpoe.database.holidays.HolidayModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository for Custom Calendars
 * Provides offline-first strategy: fetches from Firebase and caches locally in Room.
 */
class CustomCalendarRepository(context: Context) {

    //-----------------------------------------------------------------------------------------------
    // DAO setup
    //-----------------------------------------------------------------------------------------------
    private val db = AppDatabase.getCalendarDatabase(context) as AppDatabase
    private val calendarDao = db.calendarDAO()
    private val holidayDao = db.holidayDAO()

    //-----------------------------------------------------------------------------------------------
    // USER CALENDAR METHODS
    //-----------------------------------------------------------------------------------------------

    /**
     * Get all calendars for a user.
     * Tries local cache first unless `forceRefresh` is true.
     */
    suspend fun getUserCalendars(
        userId: String,
        forceRefresh: Boolean = false
    ): List<CalendarModel> {
        return withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                val cached = calendarDao.getCalendarsByUser(userId)
                if (cached.isNotEmpty()) {
                    Log.d("CustomCalRepo", "Returning ${cached.size} cached calendars")
                    return@withContext cached
                }
            }

            // Fetch from Firebase and save to local DB
            try {
                val latch = CompletableDeferred<List<CalendarModel>>()
                FirebaseCalendarDbHelper.getCalendarsForUser(userId) { calendars ->
                    GlobalScope.launch(Dispatchers.IO) {
                        calendars.forEach { cal ->
                            try {
                                val calWithOwner = cal.copy(ownerId = cal.ownerId ?: userId)
                                calendarDao.insert(calWithOwner)

                                // Save associated holidays
                                cal.holidays?.values?.forEach { holiday ->
                                    val holidayWithSource = holiday.copy(
                                        holidayId = "${cal.calendarId}:${holiday.holidayId ?: UUID.randomUUID()}",
                                        sourceId = cal.calendarId,
                                        sourceType = "custom",
                                        cachedAt = System.currentTimeMillis()
                                    )
                                    holidayDao.insert(holidayWithSource)
                                }
                            } catch (e: Exception) {
                                Log.e("CustomCalRepo", "Error saving calendar", e)
                                calendarDao.update(cal)
                            }
                        }
                        latch.complete(calendars)
                    }
                }
                latch.await()
            } catch (e: Exception) {
                Log.e("CustomCalRepo", "Error fetching from Firebase, using cache", e)
                calendarDao.getCalendarsByUser(userId)
            }
        }
    }

    /**
     * Get all holidays for a calendar.
     * Uses offline cache first unless `forceRefresh` is true.
     */
    suspend fun getHolidaysForCalendar(
        calendarId: String,
        forceRefresh: Boolean = false
    ): List<HolidayModel> {
        return withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                val cached = holidayDao.getHolidaysBySource(calendarId, "custom")
                if (cached.isNotEmpty()) {
                    Log.d("CustomCalRepo", "Returning ${cached.size} cached holidays")
                    return@withContext cached
                }
            }

            // Fetch from Firebase and save to local DB
            try {
                val latch = CompletableDeferred<List<HolidayModel>>()
                FirebaseHolidayDbHelper.getAllHolidays(calendarId) { holidays ->
                    GlobalScope.launch(Dispatchers.IO) {
                        holidays.forEach { holiday ->
                            val prefixedHoliday = holiday.copy(
                                holidayId = "$calendarId:${holiday.holidayId ?: UUID.randomUUID()}",
                                sourceId = calendarId,
                                sourceType = "custom",
                                cachedAt = System.currentTimeMillis()
                            )
                            try {
                                holidayDao.insert(prefixedHoliday)
                            } catch (e: Exception) {
                                holidayDao.update(prefixedHoliday)
                            }
                        }
                        latch.complete(holidays)
                    }
                }
                latch.await()
            } catch (e: Exception) {
                Log.e("CustomCalRepo", "Error fetching holidays from Firebase, using cache", e)
                holidayDao.getHolidaysBySource(calendarId, "custom")
            }
        }
    }

    /**
     * Get a single calendar by ID (offline-first)
     */
    suspend fun getCalendar(
        calendarId: String,
        forceRefresh: Boolean = false
    ): CalendarModel? {
        return withContext(Dispatchers.IO) {
            calendarDao.getCalendarById(calendarId)?.takeIf { !forceRefresh }
                ?: calendarDao.getCalendarById(calendarId) // fallback to cache only
        }
    }

    //-----------------------------------------------------------------------------------------------
    // SAVE / DELETE LOCAL METHODS
    //-----------------------------------------------------------------------------------------------

    /**
     * Save a calendar locally (including its holidays)
     */
    suspend fun saveCalendarLocally(calendar: CalendarModel) {
        withContext(Dispatchers.IO) {
            try {
                calendarDao.insert(calendar)
                calendar.holidays?.values?.forEach { holiday ->
                    val holidayWithSource = holiday.copy(
                        holidayId = "${calendar.calendarId}:${holiday.holidayId ?: UUID.randomUUID()}",
                        sourceId = calendar.calendarId,
                        sourceType = "custom",
                        cachedAt = System.currentTimeMillis()
                    )
                    holidayDao.insert(holidayWithSource)
                }
                Log.d("CustomCalRepo", "Calendar saved locally: ${calendar.calendarId}")
            } catch (e: Exception) {
                Log.e("CustomCalRepo", "Error saving calendar locally", e)
            }
        }
    }

    /**
     * Delete a calendar and its associated holidays locally
     */
    suspend fun deleteCalendarLocally(calendarId: String) {
        withContext(Dispatchers.IO) {
            try {
                calendarDao.getCalendarById(calendarId)?.let { calendar ->
                    calendarDao.delete(calendar)
                    holidayDao.getHolidaysBySource(calendarId, "custom")
                        .forEach { holidayDao.delete(it) }
                    Log.d("CustomCalRepo", "Calendar deleted locally: $calendarId")
                }
            } catch (e: Exception) {
                Log.e("CustomCalRepo", "Error deleting calendar locally", e)
            }
        }
    }

    //-----------------------------------------------------------------------------------------------
    // CACHE MANAGEMENT
    //-----------------------------------------------------------------------------------------------

    /**
     * Clear local cache for a specific user
     */
    suspend fun clearCacheForUser(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                calendarDao.getCalendarsByUser(userId).forEach { calendar ->
                    calendarDao.delete(calendar)
                    holidayDao.getHolidaysBySource(calendar.calendarId, "custom")
                        .forEach { holidayDao.delete(it) }
                }
                Log.d("CustomCalRepo", "Cache cleared for user: $userId")
            } catch (e: Exception) {
                Log.e("CustomCalRepo", "Error clearing cache", e)
            }
        }
    }

    /**
     * Clear all cached custom calendars and holidays
     */
    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            try {
                calendarDao.getAllCalendars().forEach { calendarDao.delete(it) }
                holidayDao.getAllHolidays()
                    .filter { it.sourceType == "custom" }
                    .forEach { holidayDao.delete(it) }
                Log.d("CustomCalRepo", "All cache cleared")
            } catch (e: Exception) {
                Log.e("CustomCalRepo", "Error clearing all cache", e)
            }
        }
    }

    /**
     * Sync all calendars and holidays from Firebase for a user
     */
    suspend fun syncFromFirebase(userId: String): Boolean {
        return try {
            getUserCalendars(userId, forceRefresh = true)
            Log.d("CustomCalRepo", "Sync completed for user: $userId")
            true
        } catch (e: Exception) {
            Log.e("CustomCalRepo", "Sync failed", e)
            false
        }
    }

    //-----------------------------------------------------------------------------------------------
    // UTILITY METHODS
    //-----------------------------------------------------------------------------------------------

    /**
     * Check if a calendar exists in local cache
     */
    suspend fun isCalendarCached(calendarId: String): Boolean {
        return withContext(Dispatchers.IO) {
            calendarDao.calendarExists(calendarId) > 0
        }
    }

    /**
     * Get basic cache info (number of calendars, holidays, last update)
     */
    suspend fun getCacheInfo(userId: String): CacheInfo {
        return withContext(Dispatchers.IO) {
            val calendars = calendarDao.getCalendarsByUser(userId)
            val totalHolidays = calendars.sumOf { calendar ->
                holidayDao.getHolidaysBySource(calendar.calendarId, "custom").size
            }

            CacheInfo(
                calendarCount = calendars.size,
                holidayCount = totalHolidays,
                lastUpdate = System.currentTimeMillis() // Placeholder, replace with actual lastUpdate if added
            )
        }
    }

    /**
     * Data class for cache information summary
     */
    data class CacheInfo(
        val calendarCount: Int,
        val holidayCount: Int,
        val lastUpdate: Long
    )
}

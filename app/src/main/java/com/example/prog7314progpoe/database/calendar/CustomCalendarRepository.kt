package com.example.prog7314progpoe.database.calendar

import android.content.Context
import android.util.Log
import com.example.prog7314progpoe.database.AppDatabase
import com.example.prog7314progpoe.database.holidays.FirebaseHolidayDbHelper
import com.example.prog7314progpoe.database.holidays.HolidayModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository with offline-first strategy for custom calendars
 * Fetches from Firebase, caches locally in Room
 */
class CustomCalendarRepository(context: Context) {

    private val db = AppDatabase.getCalendarDatabase(context) as AppDatabase
    private val calendarDao = db.calendarDAO()
    private val holidayDao = db.holidayDAO()

    /**
     * **ENHANCED: Get user's calendars - offline first**
     */
    suspend fun getUserCalendars(
        userId: String,
        forceRefresh: Boolean = false
    ): List<CalendarModel> {
        return withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                // Try local cache first
                val cached = calendarDao.getCalendarsByUser(userId)
                if (cached.isNotEmpty()) {
                    Log.d("CustomCalRepo", "Returning ${cached.size} cached calendars")
                    return@withContext cached
                }
            }

            // Fetch from Firebase
            try {
                val latch = CompletableDeferred<List<CalendarModel>>()
                FirebaseCalendarDbHelper.getUserCalendars(userId) { calendars ->
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        // Save to local database
                        calendars.forEach { cal ->
                            try {
                                // Ensure ownerId is set
                                val calWithOwner = if (cal.ownerId.isNullOrBlank()) {
                                    cal.copy(ownerId = userId)
                                } else {
                                    cal
                                }

                                calendarDao.insert(calWithOwner)

                                // Also save holidays
                                cal.holidays?.values?.forEach { holiday ->
                                    val holidayWithSource = HolidayModel(
                                        holidayId = "${cal.calendarId}:${holiday.holidayId ?: UUID.randomUUID()}",
                                        name = holiday.name,
                                        desc = holiday.desc,
                                        date = holiday.date,
                                        dateStart = holiday.dateStart,
                                        dateEnd = holiday.dateEnd,
                                        timeStart = holiday.timeStart,
                                        timeEnd = holiday.timeEnd,
                                        repeat = holiday.repeat,
                                        type = holiday.type,
                                        country = holiday.country,
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
                // Fallback to cache on error
                calendarDao.getCalendarsByUser(userId)
            }
        }
    }

    /**
     * **ENHANCED: Get holidays for a calendar - offline first**
     */
    suspend fun getHolidaysForCalendar(
        calendarId: String,
        forceRefresh: Boolean = false
    ): List<HolidayModel> {
        return withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                // Try local cache first
                val cached = holidayDao.getHolidaysBySource(calendarId, "custom")
                if (cached.isNotEmpty()) {
                    Log.d("CustomCalRepo", "Returning ${cached.size} cached holidays")
                    return@withContext cached
                }
            }

            // Fetch from Firebase
            try {
                val latch = CompletableDeferred<List<HolidayModel>>()
                FirebaseHolidayDbHelper.getAllHolidays(calendarId) { holidays ->
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        holidays.forEach { holiday ->
                            val prefixedHoliday = HolidayModel(
                                holidayId = "$calendarId:${holiday.holidayId ?: UUID.randomUUID()}",
                                name = holiday.name,
                                desc = holiday.desc,
                                date = holiday.date,
                                dateStart = holiday.dateStart,
                                dateEnd = holiday.dateEnd,
                                timeStart = holiday.timeStart,
                                timeEnd = holiday.timeEnd,
                                repeat = holiday.repeat,
                                type = holiday.type,
                                country = holiday.country,
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
                // Fallback to cache on error
                holidayDao.getHolidaysBySource(calendarId, "custom")
            }
        }
    }

    /**
     * **NEW: Get single calendar by ID - offline first**
     */
    suspend fun getCalendar(
        calendarId: String,
        forceRefresh: Boolean = false
    ): CalendarModel? {
        return withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                val cached = calendarDao.getCalendarById(calendarId)
                if (cached != null) {
                    return@withContext cached
                }
            }

            // If not in cache or force refresh, would need to fetch from Firebase
            // For now, return cache only
            calendarDao.getCalendarById(calendarId)
        }
    }

    /**
     * **NEW: Save a single calendar locally**
     */
    suspend fun saveCalendarLocally(calendar: CalendarModel) {
        withContext(Dispatchers.IO) {
            try {
                calendarDao.insert(calendar)

                // Also save holidays if present
                calendar.holidays?.values?.forEach { holiday ->
                    val holidayWithSource = HolidayModel(
                        holidayId = "${calendar.calendarId}:${holiday.holidayId ?: UUID.randomUUID()}",
                        name = holiday.name,
                        desc = holiday.desc,
                        date = holiday.date,
                        dateStart = holiday.dateStart,
                        dateEnd = holiday.dateEnd,
                        timeStart = holiday.timeStart,
                        timeEnd = holiday.timeEnd,
                        repeat = holiday.repeat,
                        type = holiday.type,
                        country = holiday.country,
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
     * **NEW: Delete calendar locally**
     */
    suspend fun deleteCalendarLocally(calendarId: String) {
        withContext(Dispatchers.IO) {
            try {
                val calendar = calendarDao.getCalendarById(calendarId)
                if (calendar != null) {
                    calendarDao.delete(calendar)

                    // Also delete associated holidays
                    val holidays = holidayDao.getHolidaysBySource(calendarId, "custom")
                    holidays.forEach { holidayDao.delete(it) }

                    Log.d("CustomCalRepo", "Calendar deleted locally: $calendarId")
                }
            } catch (e: Exception) {
                Log.e("CustomCalRepo", "Error deleting calendar locally", e)
            }
        }
    }

    /**
     * Clear local cache for a specific user
     */
    suspend fun clearCacheForUser(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val calendars = calendarDao.getCalendarsByUser(userId)
                calendars.forEach { calendar ->
                    calendarDao.delete(calendar)

                    // Delete associated holidays
                    calendar.calendarId?.let { calId ->
                        val holidays = holidayDao.getHolidaysBySource(calId, "custom")
                        holidays.forEach { holidayDao.delete(it) }
                    }
                }
                Log.d("CustomCalRepo", "Cache cleared for user: $userId")
            } catch (e: Exception) {
                Log.e("CustomCalRepo", "Error clearing cache", e)
            }
        }
    }

    /**
     * Clear all local cache
     */
    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            try {
                val calendars = calendarDao.getAllCalendars()
                calendars.forEach { calendarDao.delete(it) }

                val holidays = holidayDao.getAllHolidays()
                    .filter { it.sourceType == "custom" }
                holidays.forEach { holidayDao.delete(it) }

                Log.d("CustomCalRepo", "All cache cleared")
            } catch (e: Exception) {
                Log.e("CustomCalRepo", "Error clearing all cache", e)
            }
        }
    }

    /**
     * **ENHANCED: Sync all data from Firebase (full refresh) for a user**
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

    /**
     * **NEW: Check if calendar exists locally**
     */
    suspend fun isCalendarCached(calendarId: String): Boolean {
        return withContext(Dispatchers.IO) {
            calendarDao.calendarExists(calendarId) > 0
        }
    }

    /**
     * **NEW: Get cache status info**
     */
    suspend fun getCacheInfo(userId: String): CacheInfo {
        return withContext(Dispatchers.IO) {
            val calendars = calendarDao.getCalendarsByUser(userId)
            val totalHolidays = calendars.sumOf { calendar ->
                calendar.calendarId?.let { calId ->
                    holidayDao.getHolidaysBySource(calId, "custom").size
                } ?: 0
            }

            CacheInfo(
                calendarCount = calendars.size,
                holidayCount = totalHolidays,
                lastUpdate = calendars.maxOfOrNull {
                    // Assuming you add a lastUpdated field to CalendarModel
                    System.currentTimeMillis()
                } ?: 0
            )
        }
    }

    data class CacheInfo(
        val calendarCount: Int,
        val holidayCount: Int,
        val lastUpdate: Long
    )
}
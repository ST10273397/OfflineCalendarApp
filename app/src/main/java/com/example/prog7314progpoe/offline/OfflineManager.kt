package com.example.prog7314progpoe.offline

import android.content.Context
import android.util.Log
import com.example.prog7314progpoe.database.AppDatabase
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.example.prog7314progpoe.database.calendar.FirebaseCalendarDbHelper
import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.example.prog7314progpoe.database.user.UserModel
import com.example.prog7314progpoe.api.HolidayRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Build
import java.time.LocalDate
import java.util.Calendar
import android.content.SharedPreferences
import androidx.annotation.RequiresApi
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID

class OfflineManager(private val context: Context) {
    private val db = AppDatabase.getCalendarDatabase(context)
    private val calendarDao = db.calendarDAO()
    private val holidayDao = db.holidayDAO()
    private val userDao = db.userDAO()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val PREFS_NAME = "offline_sync_prefs"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ========== USER MANAGEMENT ==========

    /**
     * Save user to local database (called on every login)
     */
    suspend fun saveUser(user: UserModel, updateLastLogin: Boolean = true) = withContext(Dispatchers.IO) {
        try {
            val userToSave = if (updateLastLogin) {
                user.copy(lastLoginAt = System.currentTimeMillis())
            } else {
                user
            }
            userDao.insert(userToSave)
            Log.d("OfflineManager", "User saved: ${user.email}")
        } catch (e: Exception) {
            Log.e("OfflineManager", "Error saving user", e)
        }
    }

    /**
     * Save current user as primary offline user (for biometric login)
     */
    suspend fun savePrimaryUser(user: UserModel) = withContext(Dispatchers.IO) {
        try {
            userDao.clearAllPrimaryFlags()
            val userWithPrimary = user.copy(
                isPrimary = true,
                lastLoginAt = System.currentTimeMillis()
            )
            userDao.insert(userWithPrimary)
            Log.d("OfflineManager", "Primary user set: ${user.email}")
        } catch (e: Exception) {
            Log.e("OfflineManager", "Error setting primary user", e)
        }
    }

    /**
     * Get the primary offline user (for biometric login)
     */
    suspend fun getPrimaryUser(): UserModel? = withContext(Dispatchers.IO) {
        try {
            userDao.getPrimaryUser()
        } catch (e: Exception) {
            Log.e("OfflineManager", "Error getting primary user", e)
            null
        }
    }

    /**
     * **NEW: Get user by email (for offline login)**
     */
    suspend fun getUserByEmail(email: String): UserModel? = withContext(Dispatchers.IO) {
        try {
            userDao.getUserByEmail(email.trim().lowercase())
        } catch (e: Exception) {
            Log.e("OfflineManager", "Error getting user by email", e)
            null
        }
    }

    /**
     * **NEW: Get all saved users sorted by last login**
     */
    suspend fun getAllUsers(): List<UserModel> = withContext(Dispatchers.IO) {
        try {
            userDao.getAllUsersSortedByLogin()
        } catch (e: Exception) {
            Log.e("OfflineManager", "Error getting all users", e)
            emptyList()
        }
    }

    /**
     * **NEW: Offline login validation**
     */
    suspend fun validateOfflineLogin(email: String, password: String): UserModel? =
        withContext(Dispatchers.IO) {
            try {
                val user = userDao.getUserByEmail(email.trim().lowercase())
                if (user != null && user.password == password.trim()) {
                    // Update last login timestamp
                    userDao.updateLastLogin(user.userId, System.currentTimeMillis())
                    user
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("OfflineManager", "Error validating offline login", e)
                null
            }
        }

    /**
     * **NEW: Delete a user from local storage**
     */
    suspend fun deleteUser(userId: String) = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUserById(userId)
            if (user != null) {
                userDao.delete(user)
                // Also delete user's calendars and holidays
                deleteUserDataLocally(userId)
            }
        } catch (e: Exception) {
            Log.e("OfflineManager", "Error deleting user", e)
        }
    }

    // ========== CALENDAR MANAGEMENT ==========

    /**
     * **ENHANCED: Save dashboard calendars AND their holidays to offline storage**
     */
    suspend fun saveDashboardCalendarsOffline(
        calendars: List<CalendarModel>,
        userId: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val targetUserId = userId ?: auth.currentUser?.uid ?: return@withContext

            // Don't delete all - only delete this user's calendars
            deleteUserCalendarsLocally(targetUserId)

            calendars.forEach { calendar ->
                // Add userId to calendar for filtering
                val calendarWithUser = calendar.copy(
                    ownerId = targetUserId // Use ownerId to track which user it belongs to
                )
                calendarDao.insert(calendarWithUser)

                // Save each calendar's holidays
                calendar.holidays?.values?.forEach { holiday ->
                    val holidayWithSource = HolidayModel(
                        holidayId = holiday.holidayId ?: UUID.randomUUID().toString(),
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
            }
            Log.d("OfflineManager", "Saved ${calendars.size} calendars for user $targetUserId")
        } catch (e: Exception) {
            Log.e("OfflineManager", "Error saving calendars offline", e)
        }
    }

    /**
     * **NEW: Save public holidays from API**
     */
    /**
     * **ENHANCED: Save public holidays from API with logging**
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun savePublicHolidaysOffline(
        countryIso: String,
        year: Int,
        holidays: List<HolidayModel>
    ) = withContext(Dispatchers.IO) {
        try {
            val normalized = countryIso.trim().lowercase(Locale.ROOT)
            Log.d("OfflineManager", "Saving ${holidays.size} holidays for $normalized/$year")

            holidays.forEachIndexed { index, holiday ->
                // candidate iso & parsed date
                val candidateIso = getHolidayCandidateIso(holiday)
                val parsedDate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    parseIsoToLocalDate(candidateIso)
                } else null

                val holidayIdGenerated = "${normalized}_${year}_${holiday.holidayId ?: UUID.randomUUID()}"

                val holidayWithSource = holiday.copy(
                    holidayId = holidayIdGenerated,
                    sourceId = normalized,
                    sourceType = "public",
                    cachedAt = System.currentTimeMillis()
                )
                // Save
                holidayDao.insert(holidayWithSource)

                // Debug logging to help spot malformed dates
                Log.d("OfflineManager", "Saved: ${holiday.name} iso=$candidateIso parsed=$parsedDate (ID: ${holidayWithSource.holidayId})")
            }

            Log.d("OfflineManager", "Successfully saved ${holidays.size} holidays for $normalized/$year")
        } catch (e: Exception) {
            Log.e("OfflineManager", "Error saving public holidays for $countryIso/$year", e)
            e.printStackTrace()
        }
    }


    /**
     * **NEW: Get cached public holidays**
     */
    /**
     * **ENHANCED: Get cached public holidays with optional year filter**
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getOfflinePublicHolidays(
        countryIso: String,
        year: Int? = null
    ): List<HolidayModel> = withContext(Dispatchers.IO) {
        try {
            val normalized = countryIso.trim().lowercase(Locale.ROOT)
            // If your DAO filters by sourceId, ensure sourceId is stored normalized (we do that when saving)
            val allHolidays = holidayDao.getPublicHolidays(normalized)

            if (year == null) return@withContext allHolidays

            // Filter by year using robust parser
            val filtered = allHolidays.filter { holiday ->
                val candidateIso = getHolidayCandidateIso(holiday)
                val d = parseIsoToLocalDate(candidateIso)
                d?.year == year
            }
            filtered
        } catch (e: Exception) {
            Log.e("OfflineManager", "Error getting offline public holidays", e)
            emptyList()
        }
    }

    /**
     * **NEW: Get holidays for a custom calendar**
     */
    suspend fun getOfflineCustomHolidays(calendarId: String): List<HolidayModel> =
        withContext(Dispatchers.IO) {
            try {
                holidayDao.getHolidaysBySource(calendarId, "custom")
            } catch (e: Exception) {
                Log.e("OfflineManager", "Error getting offline custom holidays", e)
                emptyList()
            }
        }

    /**
     * **ENHANCED: Get offline calendars for specific user**
     */
    suspend fun getOfflineCalendars(userId: String? = null): List<CalendarModel> =
        withContext(Dispatchers.IO) {
            try {
                val targetUserId = userId ?: auth.currentUser?.uid
                val allCalendars = calendarDao.getAllCalendars()

                if (targetUserId != null) {
                    allCalendars.filter { it.ownerId == targetUserId }
                } else {
                    allCalendars
                }
            } catch (e: Exception) {
                Log.e("OfflineManager", "Error getting offline calendars", e)
                emptyList()
            }
        }


    /**
     * **ENHANCED: Sync ONLY dashboard slot calendars from Firebase to offline storage**
     */
    suspend fun syncDashboardToOffline(
        userId: String? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val uid = userId ?: auth.currentUser?.uid
        if (uid == null) {
            onComplete(false)
            return
        }

        withContext(Dispatchers.IO) {
            try {
                // Get user's dashboard preferences
                val userPrefsName = "dashboard_slots_$uid"
                val userPrefs = context.getSharedPreferences(userPrefsName, Context.MODE_PRIVATE)

                // Collect calendar IDs from dashboard slots
                val dashboardCalendarIds = mutableSetOf<String>()
                for (i in 0 until 8) {
                    val type = userPrefs.getString("type_$i", null)
                    val id = userPrefs.getString("id_$i", null)

                    if (type == "CUSTOM" && id != null) {
                        dashboardCalendarIds.add(id)
                    }
                }

                if (dashboardCalendarIds.isEmpty()) {
                    Log.d("OfflineManager", "No custom calendars in dashboard slots")
                    onComplete(true)
                    return@withContext
                }

                // Fetch ONLY the calendars that are in dashboard slots
                val calendarsDeferred = CompletableDeferred<List<CalendarModel>>()
                FirebaseCalendarDbHelper.getUserCalendars(uid) { allCalendars ->
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        // Filter to only dashboard calendars
                        val dashboardCalendars = allCalendars.filter { calendar ->
                            calendar.calendarId in dashboardCalendarIds
                        }
                        calendarsDeferred.complete(dashboardCalendars)
                    }
                }

                val calendars = calendarsDeferred.await()
                saveDashboardCalendarsOffline(calendars, uid)

                // Update last sync timestamp
                prefs.edit()
                    .putLong("${KEY_LAST_SYNC}_$uid", System.currentTimeMillis())
                    .apply()

                Log.d("OfflineManager", "Synced ${calendars.size} dashboard calendars for user $uid")
                onComplete(true)
            } catch (e: Exception) {
                Log.e("OfflineManager", "Error syncing dashboard", e)
                onComplete(false)
            }
        }
    }

    /**
     * **ENHANCED: Sync public holidays for dashboard slots (next 3 months from today)**
     */
    suspend fun syncPublicHolidaysForDashboard(
        userId: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!isOnline()) {
            onComplete(false)
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val apiRepo = HolidayRepository(context)

                // **FIXED: Calculate date range - today + 3 months**
                val today = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    LocalDate.now()
                } else {
                    val cal = Calendar.getInstance()
                    // Convert to pseudo-LocalDate for SDK < 26
                    null // We'll handle this below
                }

                // Get user's dashboard preferences
                val userPrefsName = "dashboard_slots_$userId"
                val userPrefs = context.getSharedPreferences(userPrefsName, Context.MODE_PRIVATE)

                // **FIXED: Determine which years to fetch based on current date**
                val yearsToFetch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val threeMonthsLater = today!!.plusMonths(3)
                    if (today.year == threeMonthsLater.year) {
                        // All within same year
                        listOf(today.year)
                    } else {
                        // Spans two years
                        listOf(today.year, threeMonthsLater.year)
                    }
                } else {
                    // Fallback for older Android
                    val cal = Calendar.getInstance()
                    val currentYear = cal.get(Calendar.YEAR)
                    val currentMonth = cal.get(Calendar.MONTH)
                    if (currentMonth >= 10) { // Oct, Nov, Dec - fetch next year too
                        listOf(currentYear, currentYear + 1)
                    } else {
                        listOf(currentYear)
                    }
                }

                // Get all PUBLIC slots from dashboard
                for (i in 0 until 8) {
                    val type = userPrefs.getString("type_$i", null)
                    val id = userPrefs.getString("id_$i", null)

                    if (type == "PUBLIC" && id != null) {
                        for (year in yearsToFetch) {
                            try {
                                val response = apiRepo.getHolidays(id, year)
                                val holidays = response.response?.holidays ?: emptyList()

                                // **IMPORTANT: Save ALL holidays, filtering happens at display time**
                                savePublicHolidaysOffline(id, year, holidays)

                                Log.d("OfflineManager",
                                    "Synced ${holidays.size} holidays for $id/$year")
                            } catch (e: Exception) {
                                Log.e("OfflineManager", "Error fetching holidays for $id/$year", e)
                            }
                        }
                    }
                }

                onComplete(true)
            } catch (e: Exception) {
                Log.e("OfflineManager", "Error syncing public holidays", e)
                onComplete(false)
            }
        }
    }

    // ========== CLEANUP METHODS ==========

    /**
     * **NEW: Delete user's calendars locally**
     */
    private suspend fun deleteUserCalendarsLocally(userId: String) {
        val userCalendars = calendarDao.getAllCalendars().filter { it.ownerId == userId }
        userCalendars.forEach { calendar ->
            calendarDao.delete(calendar)
            // Also delete associated holidays
            calendar.calendarId?.let { calId ->
                val holidays = holidayDao.getHolidaysBySource(calId, "custom")
                holidays.forEach { holidayDao.delete(it) }
            }
        }
    }

    /**
     * **NEW: Delete all user data locally**
     */
    private suspend fun deleteUserDataLocally(userId: String) {
        deleteUserCalendarsLocally(userId)
        // Clear user prefs
        val userPrefsName = "dashboard_slots_$userId"
        context.getSharedPreferences(userPrefsName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    /**
     * Clean old cache (call periodically)
     */
    suspend fun cleanOldCache() = withContext(Dispatchers.IO) {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        holidayDao.clearOldCache(thirtyDaysAgo)
    }

    // ========== UTILITY METHODS ==========

    /**
     * Check if device is online
     */
    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(
            android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
    }

    /**
     * Get last sync timestamp for a user
     */
    fun getLastSyncTime(userId: String? = null): Long {
        val uid = userId ?: auth.currentUser?.uid ?: return 0
        return prefs.getLong("${KEY_LAST_SYNC}_$uid", 0)
    }

    // Requires API 26+ for java.time classes (you already @RequiresApi many places)
    /**
     * Robust ISO -> LocalDate parser that handles:
     *  - "YYYY-MM-DD"
     *  - "YYYY-MM-DDTHH:MM:SS"
     *  - "YYYY-MM-DDTHH:MM:SS+00:00" (offset)
     *  - trailing 'Z' or +offset
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun parseIsoToLocalDate(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null
        // Try several strategies
        try {
            // 1) plain date at front (safe)
            if (iso.length >= 10) {
                val first10 = iso.substring(0, 10)
                runCatching { return LocalDate.parse(first10) }
            }

            // 2) try OffsetDateTime
            runCatching {
                val odt = OffsetDateTime.parse(iso)
                return odt.toLocalDate()
            }

            // 3) try LocalDateTime
            runCatching {
                val ldt = LocalDateTime.parse(iso)
                return ldt.toLocalDate()
            }
        } catch (e: Exception) {
            // fall through to heuristic
        }

        // Heuristic cleanup: strip timezone suffixes then parse first 10 chars
        return try {
            var cleaned = iso
            // remove trailing Z
            cleaned = cleaned.removeSuffix("Z")
            // strip after '+' or '-' timezone markers if present (e.g. +02:00 or -05:00)
            val posPlus = cleaned.indexOf('+')
            val posMinus = cleaned.indexOf('-', 10) // ignore the leading '-' for negative years; start after day
            val cutPos = listOf(posPlus.takeIf { it > 0 } ?: -1, posMinus.takeIf { it > 0 } ?: -1)
                .filter { it > 0 }.minOrNull() ?: -1
            if (cutPos > 0) cleaned = cleaned.substring(0, cutPos)
            if (cleaned.length >= 10) LocalDate.parse(cleaned.substring(0, 10)) else null
        } catch (e: Exception) {
            null
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    fun getHolidayCandidateIso(holiday: com.example.prog7314progpoe.database.holidays.HolidayModel): String? {
        // Keep the same null-safe field names you already used (date?.iso etc.)
        return holiday.date?.iso ?: holiday.dateStart?.iso ?: holiday.dateEnd?.iso
    }

}
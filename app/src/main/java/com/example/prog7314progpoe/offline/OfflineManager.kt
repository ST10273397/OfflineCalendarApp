package com.example.prog7314progpoe.offline

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.prog7314progpoe.api.HolidayRepository
import com.example.prog7314progpoe.database.AppDatabase
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.example.prog7314progpoe.database.calendar.FirebaseCalendarDbHelper
import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.example.prog7314progpoe.database.user.UserModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OfflineManager
 *
 * Responsible for storing and retrieving the app's calendar, holiday and user data
 * to/from a local Room DB so the app can function while offline.
 *
 * This cleaned-up version focuses on:
 *  - clearer comments
 *  - safer coroutine usage (avoids GlobalScope)
 *  - normalized IDs/keys for lookups
 *  - small defensive checks and better logging
 */
class OfflineManager(private val context: Context) {
    private val db = AppDatabase.getCalendarDatabase(context)
    private val calendarDao = db.calendarDAO()
    private val holidayDao = db.holidayDAO()
    private val userDao = db.userDAO()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val PREFS_NAME = "offline_sync_prefs"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val TAG = "OfflineManager"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // -------------------- USER MANAGEMENT --------------------

    /**
     * Save or update a user in local DB. Optionally updates the lastLoginAt timestamp.
     */
    suspend fun saveUser(user: UserModel, updateLastLogin: Boolean = true) = withContext(Dispatchers.IO) {
        try {
            val userToSave = if (updateLastLogin) user.copy(lastLoginAt = System.currentTimeMillis()) else user
            userDao.insert(userToSave)
            Log.d(TAG, "User saved: ${user.email}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user", e)
        }
    }

    /**
     * Mark a user as the primary offline user (for biometric quick-login flows).
     * This clears previous primary flags and sets the provided user.
     */
    suspend fun savePrimaryUser(user: UserModel) = withContext(Dispatchers.IO) {
        try {
            userDao.clearAllPrimaryFlags()
            val userWithPrimary = user.copy(isPrimary = true, lastLoginAt = System.currentTimeMillis())
            userDao.insert(userWithPrimary)
            Log.d(TAG, "Primary user set: ${user.email}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting primary user", e)
        }
    }

    suspend fun getPrimaryUser(): UserModel? = withContext(Dispatchers.IO) {
        try {
            userDao.getPrimaryUser()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting primary user", e)
            null
        }
    }

    /**
     * Retrieve a user (case-insensitive) by email from the local DB.
     */
    suspend fun getUserByEmail(email: String): UserModel? = withContext(Dispatchers.IO) {
        try {
            userDao.getUserByEmail(email.trim().lowercase())
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user by email", e)
            null
        }
    }

    suspend fun getAllUsers(): List<UserModel> = withContext(Dispatchers.IO) {
        try {
            userDao.getAllUsersSortedByLogin()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all users", e)
            emptyList()
        }
    }

    /**
     * Validate credentials against the locally stored user record.
     * NOTE: passwords are compared as-is here; consider hashing in future.
     */
    suspend fun validateOfflineLogin(email: String, password: String): UserModel? =
        withContext(Dispatchers.IO) {
            try {
                val user = userDao.getUserByEmail(email.trim().lowercase())
                if (user != null && user.password == password.trim()) {
                    userDao.updateLastLogin(user.userId, System.currentTimeMillis())
                    user
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Error validating offline login", e)
                null
            }
        }

    suspend fun deleteUser(userId: String) = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUserById(userId)
            if (user != null) {
                userDao.delete(user)
                deleteUserDataLocally(userId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user", e)
        }
    }

    // -------------------- CALENDAR MANAGEMENT --------------------

    /**
     * Save dashboard calendars for a specific user and persist the calendar's holidays.
     * Each calendar is associated with ownerId so we can filter by user later.
     */
    suspend fun saveDashboardCalendarsOffline(calendars: List<CalendarModel>, userId: String? = null) =
        withContext(Dispatchers.IO) {
            try {
                val targetUserId = userId ?: auth.currentUser?.uid
                if (targetUserId == null) {
                    Log.w(TAG, "No target user id available - skipping saveDashboardCalendarsOffline")
                    return@withContext
                }

                // Delete only this user's calendars (not global DB) to avoid cross-user trashing
                deleteUserCalendarsLocally(targetUserId)

                calendars.forEach { calendar ->
                    val calendarWithUser = calendar.copy(ownerId = targetUserId)
                    calendarDao.insert(calendarWithUser)

                    // Persist custom calendar holidays (if any)
                    calendar.holidays?.values?.forEach { holiday ->
                        val holidayToSave = HolidayModel(
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
                        holidayDao.insert(holidayToSave)
                    }
                }
                Log.d(TAG, "Saved ${calendars.size} calendars for user $targetUserId")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving calendars offline", e)
            }
        }

    // -------------------- PUBLIC HOLIDAYS --------------------

    /**
     * Save public holidays returned from an API into local cache.
     * This stores a normalized holidayId to make lookups deterministic.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun savePublicHolidaysOffline(countryIso: String, year: Int, holidays: List<HolidayModel>) =
        withContext(Dispatchers.IO) {
            try {
                val normalized = countryIso.trim().lowercase(Locale.ROOT)
                Log.d(TAG, "Saving ${holidays.size} holidays for $normalized/$year")

                holidays.forEach { holiday ->
                    val candidateIso = getHolidayCandidateIso(holiday)
                    val parsedDate = parseIsoToLocalDate(candidateIso)
                    val holidayIdGenerated = "${normalized}_${year}_${holiday.holidayId ?: UUID.randomUUID()}"

                    val holidayWithSource = holiday.copy(
                        holidayId = holidayIdGenerated,
                        sourceId = normalized,
                        sourceType = "public",
                        cachedAt = System.currentTimeMillis()
                    )

                    holidayDao.insert(holidayWithSource)
                    Log.d(TAG, "Saved: ${holiday.name} iso=$candidateIso parsed=$parsedDate (ID: ${holidayWithSource.holidayId})")
                }

                Log.d(TAG, "Successfully saved ${holidays.size} holidays for $normalized/$year")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving public holidays for $countryIso/$year", e)
            }
        }

    /**
     * Return cached public holidays for a given country (optional year filter).
     * When year is provided we robustly parse ISO strings and compare years.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getOfflinePublicHolidays(countryIso: String, year: Int? = null): List<HolidayModel> =
        withContext(Dispatchers.IO) {
            try {
                val normalized = countryIso.trim().lowercase(Locale.ROOT)
                val allHolidays = holidayDao.getPublicHolidays(normalized)
                if (year == null) return@withContext allHolidays

                allHolidays.filter { holiday ->
                    val candidateIso = getHolidayCandidateIso(holiday)
                    val d = parseIsoToLocalDate(candidateIso)
                    d?.year == year
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting offline public holidays", e)
                emptyList()
            }
        }

    /**
     * Returns holidays associated with a custom calendar.
     */
    suspend fun getOfflineCustomHolidays(calendarId: String): List<HolidayModel> = withContext(Dispatchers.IO) {
        try {
            holidayDao.getHolidaysBySource(calendarId, "custom")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting offline custom holidays", e)
            emptyList()
        }
    }

    /**
     * Get saved calendars. If userId is provided, filter by ownerId to show only that user's calendars.
     */
    suspend fun getOfflineCalendars(userId: String? = null): List<CalendarModel> = withContext(Dispatchers.IO) {
        try {
            val targetUserId = userId ?: auth.currentUser?.uid
            val allCalendars = calendarDao.getAllCalendars()
            if (targetUserId != null) allCalendars.filter { it.ownerId == targetUserId } else allCalendars
        } catch (e: Exception) {
            Log.e(TAG, "Error getting offline calendars", e)
            emptyList()
        }
    }

    // -------------------- SYNC HELPERS --------------------

    /**
     * Sync ONLY the dashboard slot calendars (custom) to offline DB for the given user.
     * Uses a suspendCancellableCoroutine to wait for the Firebase async callback instead of GlobalScope.
     */
    suspend fun syncDashboardToOffline(userId: String, onComplete: (Boolean) -> Unit = {}) {
        val uid = userId ?: auth.currentUser?.uid
        if (uid == null) {
            onComplete(false)
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val userPrefsName = "dashboard_slots_$uid"
                val userPrefs = context.getSharedPreferences(userPrefsName, Context.MODE_PRIVATE)

                // Collect custom calendar IDs that are on the user's dashboard
                val dashboardCalendarIds = mutableSetOf<String>()
                for (i in 0 until 8) {
                    val type = userPrefs.getString("type_$i", null)
                    val id = userPrefs.getString("id_$i", null)
                    if (type == "CUSTOM" && !id.isNullOrBlank()) dashboardCalendarIds.add(id)
                }

                if (dashboardCalendarIds.isEmpty()) {
                    Log.d(TAG, "No custom calendars in dashboard slots")
                    onComplete(true)
                    return@withContext
                }

                // Fetch user's calendars from Firebase (suspend until callback)
                val calendars = suspendCancellableCoroutine<List<CalendarModel>> { cont ->
                    try {
                        FirebaseCalendarDbHelper.getCalendarsForUser(userId) { allCalendars ->
                            val dashboardCalendars = allCalendars.filter { calendar -> calendar.calendarId in dashboardCalendarIds }
                            cont.resume(dashboardCalendars)
                        }
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }

                saveDashboardCalendarsOffline(calendars, uid)

                // Persist last sync timestamp per user
                prefs.edit().putLong("${KEY_LAST_SYNC}_$uid", System.currentTimeMillis()).apply()

                Log.d(TAG, "Synced ${calendars.size} dashboard calendars for user $uid")
                onComplete(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing dashboard", e)
                onComplete(false)
            }
        }
    }

    /**
     * Sync public holidays for the user's dashboard PUBLIC slots for the next 3 months.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun syncPublicHolidaysForDashboard(userId: String, onComplete: (Boolean) -> Unit = {}) {
        if (!isOnline()) {
            onComplete(false)
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val apiRepo = HolidayRepository(context)

                val today = LocalDate.now()
                val threeMonthsLater = today.plusMonths(3)

                // Determine which years to fetch (could span two different years)
                val yearsToFetch = if (today.year == threeMonthsLater.year) listOf(today.year) else listOf(today.year, threeMonthsLater.year)

                val userPrefsName = "dashboard_slots_$userId"
                val userPrefs = context.getSharedPreferences(userPrefsName, Context.MODE_PRIVATE)

                for (i in 0 until 8) {
                    val type = userPrefs.getString("type_$i", null)
                    val id = userPrefs.getString("id_$i", null)

                    if (type == "PUBLIC" && !id.isNullOrBlank()) {
                        for (year in yearsToFetch) {
                            try {
                                val response = apiRepo.getHolidays(id, year)
                                val holidays = response.response?.holidays ?: emptyList()
                                savePublicHolidaysOffline(id, year, holidays)
                                Log.d(TAG, "Synced ${holidays.size} holidays for $id/$year")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching holidays for $id/$year", e)
                            }
                        }
                    }
                }

                onComplete(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing public holidays", e)
                onComplete(false)
            }
        }
    }

    // -------------------- CLEANUP --------------------

    /**
     * Delete calendars that belong to a specific user and their associated custom holidays.
     */
    private suspend fun deleteUserCalendarsLocally(userId: String) = withContext(Dispatchers.IO) {
        val userCalendars = calendarDao.getAllCalendars().filter { it.ownerId == userId }
        userCalendars.forEach { calendar ->
            calendarDao.delete(calendar)
            calendar.calendarId?.let { calId ->
                val holidays = holidayDao.getHolidaysBySource(calId, "custom")
                holidays.forEach { holidayDao.delete(it) }
            }
        }
    }

    /**
     * Delete all locally stored data for a user and clear their dashboard preferences.
     */
    private suspend fun deleteUserDataLocally(userId: String) = withContext(Dispatchers.IO) {
        deleteUserCalendarsLocally(userId)
        val userPrefsName = "dashboard_slots_$userId"
        context.getSharedPreferences(userPrefsName, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /**
     * Remove cached holidays older than 30 days.
     */
    suspend fun cleanOldCache() = withContext(Dispatchers.IO) {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        holidayDao.clearOldCache(thirtyDaysAgo)
    }

    // -------------------- UTILITIES --------------------

    /**
     * Simple network availability check.
     */
    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Get last sync timestamp for a user (0 if never synced).
     */
    fun getLastSyncTime(userId: String? = null): Long {
        val uid = userId ?: auth.currentUser?.uid ?: return 0
        return prefs.getLong("${KEY_LAST_SYNC}_$uid", 0)
    }

    // -------------------- DATE PARSING HELPERS --------------------

    /**
     * Parse ISO-ish strings into LocalDate. Accepts several common formats and attempts
     * resilient parsing by stripping time/zone fragments when necessary.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun parseIsoToLocalDate(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null

        // Try simple substring parse (YYYY-MM-DD)
        if (iso.length >= 10) {
            runCatching { return LocalDate.parse(iso.substring(0, 10)) }
        }

        // Try OffsetDateTime -> LocalDate
        runCatching {
            val odt = OffsetDateTime.parse(iso)
            return odt.toLocalDate()
        }

        // Try LocalDateTime -> LocalDate
        runCatching {
            val ldt = LocalDateTime.parse(iso)
            return ldt.toLocalDate()
        }

        // Heuristic: strip trailing Z or timezone offsets then parse first 10 chars
        return try {
            var cleaned = iso
            cleaned = cleaned.removeSuffix("Z")
            val posPlus = cleaned.indexOf('+')
            val posMinus = cleaned.indexOf('-', 10) // ignore initial '-' if present
            val cutPos = listOf(posPlus.takeIf { it > 0 } ?: -1, posMinus.takeIf { it > 0 } ?: -1)
                .filter { it > 0 }.minOrNull() ?: -1
            if (cutPos > 0) cleaned = cleaned.substring(0, cutPos)
            if (cleaned.length >= 10) LocalDate.parse(cleaned.substring(0, 10)) else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed heuristic parse for iso: $iso", e)
            null
        }
    }

    /**
     * Choose a candidate ISO date string from the HolidayModel (tries date, dateStart, dateEnd).
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun getHolidayCandidateIso(holiday: com.example.prog7314progpoe.database.holidays.HolidayModel): String? {
        return holiday.date?.iso ?: holiday.dateStart?.iso ?: holiday.dateEnd?.iso
    }
}

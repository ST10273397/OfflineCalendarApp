package com.example.prog7314progpoe.workers

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.api.HolidayRepository
import com.example.prog7314progpoe.database.holidays.FirebaseHolidayDbHelper
import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.example.prog7314progpoe.offline.OfflineManager
import com.example.prog7314progpoe.ui.HomeActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Background worker that checks for upcoming holidays and sends notifications
 */
class HolidayNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val channelId = "holiday_reminders"
    private val notificationManager = NotificationManagerCompat.from(applicationContext)

    override suspend fun doWork(): Result {
        return try {
            createNotificationChannel()
            checkUpcomingHolidays()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    /**
     * Check all holidays (API + Custom) for tomorrow's date
     * Only checks calendars for the primary (last logged in) user
     */
    private suspend fun checkUpcomingHolidays() {
        val tomorrow = getTomorrowDate()

        // Get the primary user (last logged in user)
        val offlineManager = OfflineManager(applicationContext)
        val primaryUser = offlineManager.getPrimaryUser()

        if (primaryUser == null) {
            Log.d("HolidayNotificationWorker", "No primary user found")
            return
        }

        val userId = primaryUser.userId
        Log.d("HolidayNotificationWorker", "Checking holidays for user: $userId")

        // Check custom holidays for this user's calendars
        checkCustomHolidays(userId, tomorrow)

        // Check public holidays from API for this user's country
        checkPublicHolidays(userId, tomorrow)
    }

    /**
     * Check custom holidays stored in Firebase
     */
    private suspend fun checkCustomHolidays(calendarId: String, tomorrow: String) {
        try {
            val holidays = fetchCustomHolidays(calendarId) // suspend-wrapped fetch

            holidays.forEach { holiday ->
                // Try to extract an ISO yyyy-MM-dd from the holiday.date field robustly
                val dateString = holiday.date?.toString() ?: holiday.toString()
                val isoMatch = Regex("""\d{4}-\d{2}-\d{2}""").find(dateString)?.value

                if (isoMatch != null && isoMatch == tomorrow) {
                    sendNotification(
                        title = "Holiday Tomorrow!",
                        message = "${holiday.name} is tomorrow on ${formatDate(isoMatch)}",
                        notificationId = holiday.holidayId.hashCode()
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.w("HolidayNotificationWorker", "Failed checking custom holidays: ${e.localizedMessage}")
        }
    }

    /**
     * Suspend wrapper for FirebaseHolidayDbHelper.getAllHolidays(callback)
     */
    private suspend fun fetchCustomHolidays(calendarId: String): List<HolidayModel> =
        suspendCancellableCoroutine { cont ->
            try {
                FirebaseHolidayDbHelper.getAllHolidays(calendarId) { fetched ->
                    // If the callback returns null, return empty list
                    cont.resume(fetched ?: emptyList())
                }

                // If coroutine is cancelled before callback returns, nothing else needed,
                // because the callback will still call resume (we won't double-resume).
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }

    /**
     * Check public holidays from Calendarific API
     */
    private suspend fun checkPublicHolidays(userId: String, tomorrow: String) {
        try {
            val countryCode = getCountryCodeForUser(userId) ?: "US"
            val year = Calendar.getInstance().get(Calendar.YEAR)

            val repository = HolidayRepository(applicationContext)
            val response = repository.getHolidays(countryCode, year)

            response.response?.holidays?.forEach { holiday ->
                val holidayDate = holiday.date?.iso // Format: "2025-11-19"
                if (holidayDate == tomorrow) {
                    sendNotification(
                        title = "Public Holiday Tomorrow!",
                        message = "${holiday.name} is tomorrow",
                        notificationId = holiday.name.hashCode()
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get tomorrow's date in ISO format (yyyy-MM-dd)
     */
    private fun getTomorrowDate(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    /**
     * Format date for display
     */
    private fun formatDate(isoDate: String?): String {
        if (isoDate == null) return ""
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val output = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val date = input.parse(isoDate)
            output.format(date ?: Date())
        } catch (e: Exception) {
            isoDate
        }
    }

    /**
     * Send notification to user
     */
    @SuppressLint("MissingPermission")
    private fun sendNotification(title: String, message: String, notificationId: Int) {
        val intent = Intent(applicationContext, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.chronosync_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Create notification channel for Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Holiday Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming holidays"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
            }

            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }



    /**
     * Get country code from user's dashboard preferences
     */
    private fun getCountryCodeForUser(userId: String): String? {
        val prefs = applicationContext.getSharedPreferences("dashboard_slots_$userId", Context.MODE_PRIVATE)

        // Check all dashboard slots for a public calendar (country)
        for (i in 0 until 8) {
            val type = prefs.getString("type_$i", null)
            val id = prefs.getString("id_$i", null)

            if (type == "PUBLIC" && !id.isNullOrBlank()) {
                Log.d("HolidayNotificationWorker", "Found country code: $id")
                return id
            }
        }

        return null
    }
}
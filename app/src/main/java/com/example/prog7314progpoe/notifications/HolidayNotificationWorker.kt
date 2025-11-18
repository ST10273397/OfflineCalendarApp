package com.example.prog7314progpoe.workers

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.api.HolidayRepository
import com.example.prog7314progpoe.database.holidays.FirebaseHolidayDbHelper
import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.example.prog7314progpoe.ui.HomeActivity
import java.text.SimpleDateFormat
import java.util.*

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
     */
    private suspend fun checkUpcomingHolidays() {
        val tomorrow = getTomorrowDate()
        val calendarId = getActiveCalendarId() ?: return

        // Check custom holidays from Firebase
        checkCustomHolidays(calendarId, tomorrow)

        // Check public holidays from API
        checkPublicHolidays(tomorrow)
    }

    /**
     * Check custom holidays stored in Firebase
     */
    private suspend fun checkCustomHolidays(calendarId: String, tomorrow: String) {
        try {
            val holidays = mutableListOf<HolidayModel>()

            FirebaseHolidayDbHelper.getAllHolidays(calendarId) { fetchedHolidays ->
                holidays.addAll(fetchedHolidays)
            }

            // Wait a bit for callback
            kotlinx.coroutines.delay(1000)

            holidays.forEach { holiday ->
                if (holiday.date == tomorrow as HolidayModel.DateInfo) {
                    sendNotification(
                        title = "Holiday Tomorrow!",
                        message = "${holiday.name} is tomorrow on ${formatDate(holiday.date as String?)}",
                        notificationId = holiday.holidayId.hashCode()
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Check public holidays from Calendarific API
     */
    private suspend fun checkPublicHolidays(tomorrow: String) {
        try {
            val countryCode = getCountryCode() ?: "US"
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
     * Get the active calendar ID from SharedPreferences
     */
    private fun getActiveCalendarId(): String? {
        val prefs = applicationContext.getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE)
        return prefs.getString("active_calendar_id", null)
    }

    /**
     * Get country code from SharedPreferences
     */
    private fun getCountryCode(): String? {
        val prefs = applicationContext.getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE)
        return prefs.getString("country_code", "US")
    }
}
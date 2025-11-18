package com.example.prog7314progpoe.workers

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Schedules daily checks for upcoming holidays
 */
object HolidayNotificationScheduler {

    private const val WORK_NAME = "holiday_notification_check"

    /**
     * Schedule daily holiday checks at a specific hour (default: 8 AM)
     */
    fun scheduleDailyCheck(context: Context, hourOfDay: Int = 8) {
        // Calculate initial delay to next check time
        val currentTime = java.util.Calendar.getInstance()
        val targetTime = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }

        // If target time has passed today, schedule for tomorrow
        if (targetTime.before(currentTime)) {
            targetTime.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelay = targetTime.timeInMillis - currentTime.timeInMillis

        // Set up constraints (optional - only run when device conditions are met)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Needs internet for API
            .setRequiresBatteryNotLow(true) // Don't drain battery
            .build()

        // Create periodic work request (runs every 24 hours)
        val workRequest = PeriodicWorkRequestBuilder<HolidayNotificationWorker>(
            24, TimeUnit.HOURS // Repeat every 24 hours
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        // Schedule the work (replaces existing work with same name)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * Run an immediate check (useful for testing)
     */
    fun runImmediateCheck(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<HolidayNotificationWorker>()
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    /**
     * Cancel scheduled checks
     */
    fun cancelScheduledChecks(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
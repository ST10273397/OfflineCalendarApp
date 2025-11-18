package com.example.prog7314progpoe.offline

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "user_session"
        private const val KEY_USER_ID = "current_user_id"
        private const val KEY_USER_EMAIL = "current_user_email"
        private const val KEY_IS_OFFLINE = "is_offline_mode"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    /**
     * Save current session (call on successful login)
     */
    fun saveSession(userId: String, email: String, isOffline: Boolean = false) {
        prefs.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_EMAIL, email)
            putBoolean(KEY_IS_OFFLINE, isOffline)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }

    /**
     * Get current user ID
     */
    fun getCurrentUserId(): String? {
        return if (isLoggedIn()) {
            prefs.getString(KEY_USER_ID, null)
        } else {
            null
        }
    }

    /**
     * Get current user email
     */
    fun getCurrentUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /**
     * Check if in offline mode
     */
    fun isOfflineMode(): Boolean {
        return prefs.getBoolean(KEY_IS_OFFLINE, false)
    }

    /**
     * Clear session (logout)
     */
    fun clearSession() {
        prefs.edit().clear().apply()
    }

    /**
     * Update offline mode status
     */
    fun setOfflineMode(isOffline: Boolean) {
        prefs.edit().putBoolean(KEY_IS_OFFLINE, isOffline).apply()
    }
}
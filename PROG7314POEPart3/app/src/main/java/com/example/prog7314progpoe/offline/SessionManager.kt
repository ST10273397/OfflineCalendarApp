package com.example.prog7314progpoe.offline

import android.content.Context
import android.content.SharedPreferences

/**
 * Handles saving, retrieving, and clearing the user's session state.
 * This includes user ID, email, online/offline mode, and login state.
 */
class SessionManager(context: Context) {

    // SharedPreferences instance tied to the app
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "user_session"       // Name of the shared prefs file
        private const val KEY_USER_ID = "current_user_id"   // Logged-in user ID
        private const val KEY_USER_EMAIL = "current_user_email" // Logged-in user email
        private const val KEY_IS_OFFLINE = "is_offline_mode" // Whether the app is working offline
        private const val KEY_IS_LOGGED_IN = "is_logged_in" // Basic "logged-in" flag
    }

    /**
     * Saves the current user session.
     *
     * @param userId The unique ID of the logged-in user
     * @param email The email used to log in
     * @param isOffline Whether this login is offline mode
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
     * Returns the currently stored user ID if logged in.
     * If not logged in, returns null.
     */
    fun getCurrentUserId(): String? {
        return if (isLoggedIn()) {
            prefs.getString(KEY_USER_ID, null)
        } else null
    }

    /**
     * Returns the user's email if it exists, even if offline.
     */
    fun getCurrentUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    /**
     * @return True if the app considers the user logged in.
     */
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /**
     * @return True if the user is operating in offline mode.
     */
    fun isOfflineMode(): Boolean {
        return prefs.getBoolean(KEY_IS_OFFLINE, false)
    }

    /**
     * Clears all saved session data.
     * Use when the user logs out or when the app resets.
     */
    fun clearSession() {
        prefs.edit().clear().apply()
    }

    /**
     * Updates the offline mode status.
     *
     * Useful when switching between online/offline automatically
     * (e.g., network drops, user chooses offline mode, etc.)
     */
    fun setOfflineMode(isOffline: Boolean) {
        prefs.edit().putBoolean(KEY_IS_OFFLINE, isOffline).apply()
    }
}

package com.example.prog7314progpoe.ui.settings

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Notification.VISIBILITY_PUBLIC
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.Button
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.offline.SessionManager
import com.example.prog7314progpoe.ui.reglogin.AuthActivity
import com.example.prog7314progpoe.ui.HomeActivity
import com.example.prog7314progpoe.workers.HolidayNotificationScheduler
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Locale


class SettingsFragment : Fragment(R.layout.fragment_settings) {

    // -------------------------
    // Constants & SharedPreferences
    // -------------------------
    private val prefs by lazy { requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val sessionManager by lazy { SessionManager(requireContext()) }
    private val KEY_DARK_MODE = "dark_mode"

    private val KEY_LANGUAGE = "app_language" // "en" or "af"

    private val channelId = "i.apps.notifications" // Unique channel ID for notifications
    private val description = "Holiday Description"  // Description for the notification channel
    private val notificationId = 1234 // Unique identifier for the notification

    // Create an explicit intent for an Activity in your app.
    private lateinit var restartIntent: Intent

    // -------------------------
    // Lifecycle - setup UI
    // -------------------------
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // -------------------------
        // UI references
        // -------------------------
        val switchDark = view.findViewById<MaterialSwitch>(R.id.switchDark)
        val btnLogout = view.findViewById<MaterialButton>(R.id.btnLogout)
        val btnDelete = view.findViewById<MaterialButton>(R.id.btnDeleteAccount)
        val btnChangePw = view.findViewById<MaterialButton>(R.id.btnChangePassword)
        val btnTestNotif = view.findViewById<MaterialButton>(R.id.btn_testNotif)
        val switchLanguageAf = view.findViewById<MaterialSwitch>(R.id.switchLanguageAf)

        // -------------------------
        // Initialize dark mode toggle based on saved preference or current mode
        // -------------------------
        val savedMode = prefs.getBoolean(KEY_DARK_MODE, false)
        switchDark.isChecked = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> savedMode
        }

        createNotificationChannel()

        btnTestNotif.setOnClickListener {
            this.sendNotification()
        }

        // -------------------------
        // Initialize language toggle: ON = Afrikaans, OFF = English
        // -------------------------
        val savedLang = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        switchLanguageAf.isChecked = savedLang == "af"

        switchLanguageAf.setOnCheckedChangeListener { _, isChecked ->
            val langCode = if (isChecked) "af" else "en"
            applyLanguage(langCode)
        }


        // -------------------------
        // Dark mode toggle listener
        // -------------------------
        switchDark.setOnCheckedChangeListener { _, isChecked ->
            applyDarkMode(isChecked)
        }

        // -------------------------
        // Logout button listener
        // -------------------------
        btnLogout.setOnClickListener {
            logout()
            Toast.makeText(requireContext(), getString(R.string.toast_logout_success), Toast.LENGTH_SHORT).show()
        }

        // -------------------------
        // Delete account button listener
        // -------------------------
        btnDelete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_delete_account_title))
                .setMessage(getString(R.string.dialog_delete_account_message))
                .setPositiveButton(getString(R.string.dialog_button_delete)) { _, _ ->
                    val user = auth.currentUser
                    if (user == null) {
                        Toast.makeText(requireContext(), getString(R.string.toast_no_user_signed_in), Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    user.delete().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(requireContext(), getString(R.string.toast_account_deleted), Toast.LENGTH_SHORT).show()
                            startActivity(Intent(requireContext(), AuthActivity::class.java))
                        } else {
                            val msg = task.exception?.localizedMessage ?: getString(R.string.error_reauth_failed)
                            Toast.makeText(requireContext(), "$msg. ${getString(R.string.error_delete_account_reauth_required)}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton(getString(R.string.dialog_button_cancel), null)
                .show()
        }

        // -------------------------
        // Change password button listener
        // -------------------------
        btnChangePw.setOnClickListener {
            showChangePasswordDialog()
        }
    }

    // -------------------------
    // Apply and persist dark mode
    // -------------------------
    private fun applyDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        val mode = if (enabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    // -------------------------
    // Apply and persist language
    // -------------------------
    private fun applyLanguage(langCode: String) {
        // Save preference
        prefs.edit().putString(KEY_LANGUAGE, langCode).apply()

        // Update app locale
        val locale = Locale(langCode)
        Locale.setDefault(locale)

        val resources = requireContext().resources
        val config = resources.configuration
        config.setLocale(locale)

        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)

        // Recreate activity so all fragments reload strings
        requireActivity().recreate()
    }


    // -------------------------
    // Logout function - clear session, Firebase sign out, navigate back
    // -------------------------
    private fun logout() {
        lifecycleScope.launch {
            sessionManager.clearSession() // Clear stored session
            auth.signOut() // Sign out from Firebase

            val intent = Intent(requireContext(), AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    // -------------------------
    // Show change password dialog
    // -------------------------
    private fun showChangePasswordDialog() {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.toast_no_signed_in_email_user),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // -------------------------
        // Inflate custom dialog layout
        // -------------------------
        val content = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_change_password, null, false)
        val inOld = content.findViewById<TextInputEditText>(R.id.inOld)
        val inNew = content.findViewById<TextInputEditText>(R.id.inNew)
        val inConfirm = content.findViewById<TextInputEditText>(R.id.inConfirm)

        // -------------------------
        // Build dialog
        // -------------------------
        val dlg = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_change_password_title))
            .setView(content)
            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
            .setPositiveButton(
                getString(R.string.dialog_button_update),
                null
            ) // override to validate first
            .create()

        dlg.setOnShowListener {
            val btn = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val oldPw = inOld.text?.toString()?.trim().orEmpty()
                val newPw = inNew.text?.toString()?.trim().orEmpty()
                val confirm = inConfirm.text?.toString()?.trim().orEmpty()

                // -------------------------
                // Validate inputs
                // -------------------------
                when {
                    oldPw.isEmpty() || newPw.isEmpty() || confirm.isEmpty() -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.toast_fill_all_fields),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }

                    newPw.length < 6 -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.toast_password_too_short),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }

                    newPw != confirm -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.toast_passwords_mismatch),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }
                }

                // -------------------------
                // Re-authenticate with old password
                // -------------------------
                val credential = EmailAuthProvider.getCredential(email, oldPw)
                user.reauthenticate(credential).addOnCompleteListener { re ->
                    if (!re.isSuccessful) {
                        val msg = re.exception?.localizedMessage
                            ?: getString(R.string.error_reauth_failed)
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }

                    // -------------------------
                    // Update to new password
                    // -------------------------
                    user.updatePassword(newPw).addOnCompleteListener { up ->
                        if (up.isSuccessful) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.toast_password_changed),
                                Toast.LENGTH_SHORT
                            ).show()
                            dlg.dismiss()
                        } else {
                            val msg2 = up.exception?.localizedMessage
                                ?: getString(R.string.error_update_password_failed)
                            Toast.makeText(requireContext(), msg2, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        dlg.show()
    }

/**
 * Create a notification channel for devices running Android 8.0 or higher.
 * A channel groups notifications with similar behavior.
 */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Holiday Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows upcoming holiday alerts"
            }

            val manager =
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Build and send a notification with a custom layout and action.
     */
    @SuppressLint("MissingPermission")
    fun sendNotification() {
        val context = requireContext()

        // Open HomeActivity when the notification is tapped
        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.chronosync_logo)
            .setContentTitle("Holiday soon!")
            .setContentText("You have a holiday coming up soon!!")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

}
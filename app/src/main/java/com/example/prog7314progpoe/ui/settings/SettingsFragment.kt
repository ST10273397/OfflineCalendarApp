package com.example.prog7314progpoe.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.offline.SessionManager
import com.example.prog7314progpoe.ui.reglogin.AuthActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    // -------------------------
    // Constants & SharedPreferences
    // -------------------------
    private val prefs by lazy { requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val sessionManager by lazy { SessionManager(requireContext()) }
    private val KEY_DARK_MODE = "dark_mode"

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

        // -------------------------
        // Initialize dark mode toggle based on saved preference or current mode
        // -------------------------
        val savedMode = prefs.getBoolean(KEY_DARK_MODE, false)
        switchDark.isChecked = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> savedMode
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
            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
        }

        // -------------------------
        // Delete account button listener
        // -------------------------
        btnDelete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete account")
                .setMessage("This action cannot be undone")
                .setPositiveButton("Delete") { _, _ ->
                    val user = auth.currentUser
                    if (user == null) {
                        Toast.makeText(requireContext(), "No user signed in", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    user.delete().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(requireContext(), "Account deleted", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(requireContext(), AuthActivity::class.java))
                        } else {
                            val msg = task.exception?.localizedMessage ?: "Delete failed"
                            Toast.makeText(requireContext(), "$msg. Re-login may be required.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
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
            Toast.makeText(requireContext(), "No signed-in email user", Toast.LENGTH_SHORT).show()
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
            .setTitle("Change password")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Update", null) // override to validate first
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
                        Toast.makeText(requireContext(), "Fill in all fields", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    newPw.length < 6 -> {
                        Toast.makeText(requireContext(), "New password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    newPw != confirm -> {
                        Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }

                // -------------------------
                // Re-authenticate with old password
                // -------------------------
                val credential = EmailAuthProvider.getCredential(email, oldPw)
                user.reauthenticate(credential).addOnCompleteListener { re ->
                    if (!re.isSuccessful) {
                        val msg = re.exception?.localizedMessage ?: "Re-authentication failed"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }

                    // -------------------------
                    // Update to new password
                    // -------------------------
                    user.updatePassword(newPw).addOnCompleteListener { up ->
                        if (up.isSuccessful) {
                            Toast.makeText(requireContext(), "Password updated successfully", Toast.LENGTH_SHORT).show()
                            dlg.dismiss()
                        } else {
                            val msg2 = up.exception?.localizedMessage ?: "Password update failed"
                            Toast.makeText(requireContext(), msg2, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        dlg.show()
    }
}

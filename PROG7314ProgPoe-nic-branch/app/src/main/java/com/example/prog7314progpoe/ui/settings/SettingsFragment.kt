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
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.ui.reglogin.LoginActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    //SEGMENT constants and prefs - saved settings
    //-----------------------------------------------------------------------------------------------
    private val prefs by lazy { requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE) } // store small flags
    private val auth by lazy { FirebaseAuth.getInstance() } // Firebase auth
    private val KEY_DARK_MODE = "dark_mode" //Start
    //-----------------------------------------------------------------------------------------------

    //SEGMENT lifecycle - wire up the UI
    //-----------------------------------------------------------------------------------------------
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //SUB-SEGMENT find views - get references
        //-------------------------------------------------
        val switchDark = view.findViewById<MaterialSwitch>(R.id.switchDark) // dark toggle
        val btnLogout = view.findViewById<MaterialButton>(R.id.btnLogout) // logout button
        val btnDelete = view.findViewById<MaterialButton>(R.id.btnDeleteAccount) // delete btn
        val btnChangePw = view.findViewById<MaterialButton>(R.id.btnChangePassword) // change pw
        //-------------------------------------------------

        //SUB-SEGMENT init switch - reflect current mode
        //-------------------------------------------------
        val saved = prefs.getBoolean(KEY_DARK_MODE, false) // saved pref
        switchDark.isChecked = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> saved // follow saved if system or unknown
        }
        //-------------------------------------------------

        //SUB-SEGMENT toggle handler - flip theme live
        //-------------------------------------------------
        switchDark.setOnCheckedChangeListener { _, isChecked ->
            applyDarkMode(isChecked) // apply and persist
        }
        //-------------------------------------------------

        //SUB-SEGMENT logout - clear session and go to login
        //-------------------------------------------------
        btnLogout.setOnClickListener {
            auth.signOut() // sign out
            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show() //note
            goToLoginClearTask() // clear back stack
        }
        //-------------------------------------------------

        //SUB-SEGMENT delete account - confirm then attempt
        //-------------------------------------------------
        btnDelete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete account")
                .setMessage("This cannot be undone")
                .setPositiveButton("Delete") { _, _ ->
                    val user = auth.currentUser // get user
                    if (user == null) {
                        Toast.makeText(requireContext(), "No user signed in", Toast.LENGTH_SHORT).show() //note
                        return@setPositiveButton
                    }
                    user.delete().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(requireContext(), "Account deleted", Toast.LENGTH_SHORT).show() // Start
                            goToLoginClearTask() // back to login
                        } else {
                            val msg = task.exception?.localizedMessage ?: "Delete failed"
                            // Firebase may require recent login for delete
                            Toast.makeText(requireContext(), "$msg Re login may be required", Toast.LENGTH_LONG).show() //note
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        //-------------------------------------------------

        //SUB-SEGMENT change password - show dialog (old + new + confirm)
        //-------------------------------------------------
        btnChangePw.setOnClickListener {
            showChangePasswordDialog() // open dialog
        }
        //-------------------------------------------------
    }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT helpers - theme + nav
    //-----------------------------------------------------------------------------------------------
    private fun applyDarkMode(enabled: Boolean) {
        // persist pref
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply() // save it

        // apply mode
        val mode = if (enabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(mode) // flip theme live
    }

    private fun goToLoginClearTask() {
        //Navigate to login, clear back stack
        val i = Intent(requireContext(), LoginActivity::class.java) // intent
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) // flags
        startActivity(i) //start
        requireActivity().finish() // close current
    }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT password change - reauth with old then update to new
    //-----------------------------------------------------------------------------------------------
    private fun showChangePasswordDialog() {
        val user = auth.currentUser // user
        val email = user?.email // email
        if (user == null || email.isNullOrBlank()) {
            Toast.makeText(requireContext(), "No email user signed in", Toast.LENGTH_SHORT).show() //note
            return
        }

        //SUB-SEGMENT inflate - custom dialog view
        //-------------------------------------------------
        val content = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_change_password, null, false) // view
        val inOld = content.findViewById<TextInputEditText>(R.id.inOld) // current
        val inNew = content.findViewById<TextInputEditText>(R.id.inNew) // new
        val inConfirm = content.findViewById<TextInputEditText>(R.id.inConfirm) // confirm
        //-------------------------------------------------

        //SUB-SEGMENT show dialog - handle positive click
        //-------------------------------------------------
        val dlg = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change password")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Update", null) // we override to validate first
            .create()

        dlg.setOnShowListener {
            val btn = dlg.getButton(AlertDialog.BUTTON_POSITIVE) // button
            btn.setOnClickListener {
                val oldPw = inOld.text?.toString()?.trim().orEmpty() // read old
                val newPw = inNew.text?.toString()?.trim().orEmpty() // read new
                val confirm = inConfirm.text?.toString()?.trim().orEmpty() // read confirm

                //SUB-SEGMENT validate - quick checks
                //-------------------------------------------------
                if (oldPw.isEmpty() || newPw.isEmpty() || confirm.isEmpty()) {
                    Toast.makeText(requireContext(), "Fill in all fields", Toast.LENGTH_SHORT).show() // Start
                    return@setOnClickListener
                }
                if (newPw.length < 6) {
                    Toast.makeText(requireContext(), "New password must be at least 6 chars", Toast.LENGTH_SHORT).show() //comment
                    return@setOnClickListener
                }
                if (newPw != confirm) {
                    Toast.makeText(requireContext(), "Passwords dont match", Toast.LENGTH_SHORT).show() // comment
                    return@setOnClickListener
                }
                //-------------------------------------------------

                //SUB-SEGMENT reauth - verify old password
                //-------------------------------------------------
                val cred = EmailAuthProvider.getCredential(email, oldPw) // credential
                user.reauthenticate(cred).addOnCompleteListener { re ->
                    if (!re.isSuccessful) {
                        val msg = re.exception?.localizedMessage ?: "Reauth failed"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show() //note
                        return@addOnCompleteListener
                    }

                    //SUB-SEGMENT update - set the new password
                    //-------------------------------------------------
                    user.updatePassword(newPw).addOnCompleteListener { up ->
                        if (up.isSuccessful) {
                            Toast.makeText(requireContext(), "Password updated", Toast.LENGTH_SHORT).show() // Start
                            dlg.dismiss() // close
                        } else {
                            val msg2 = up.exception?.localizedMessage ?: "Update failed"
                            Toast.makeText(requireContext(), msg2, Toast.LENGTH_LONG).show() //comment
                        }
                    }
                    //-------------------------------------------------
                }
                //-------------------------------------------------
            }
        }

        dlg.show() // show
        //-------------------------------------------------
    }
    //-----------------------------------------------------------------------------------------------
}
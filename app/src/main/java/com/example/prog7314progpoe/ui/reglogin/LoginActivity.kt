package com.example.prog7314progpoe.ui.reglogin

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.prog7314progpoe.ui.HomeActivity
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.user.FirebaseUserDbHelper
import com.example.prog7314progpoe.database.user.UserModel
import com.example.prog7314progpoe.offline.OfflineManager
import com.example.prog7314progpoe.offline.SessionManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var offlineManager: OfflineManager
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        auth = Firebase.auth
        offlineManager = OfflineManager(this)
        sessionManager = SessionManager(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val emailEt = findViewById<EditText>(R.id.etEmail)
        val passwordEt = findViewById<EditText>(R.id.etPassword)
        val loginBtn = findViewById<Button>(R.id.btnLogin)
        val signUpLink = findViewById<TextView>(R.id.tvSignUp)

        // **ENHANCED: Email/Password login with offline support**
        loginBtn.setOnClickListener {
            val email = emailEt.text.toString().trim()
            val password = passwordEt.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                if (offlineManager.isOnline()) {
                    loginOnline(email, password)
                } else {
                    loginOffline(email, password)
                }
            }
        }

        signUpLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    /**
     * Handle online login
     */
    private fun loginOnline(email: String, password: String) {
        FirebaseUserDbHelper.loginUser(email, password) { success, message ->
            if (success) {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    // Fetch full user model and save locally
                    FirebaseUserDbHelper.getUser(currentUser.uid) { userModel ->
                        if (userModel != null) {
                            lifecycleScope.launch {
                                // **SAVE SESSION**
                                sessionManager.saveSession(
                                    userId = currentUser.uid,
                                    email = email,
                                    isOffline = false
                                )

                                // Save user with password for offline access
                                val userWithPassword = userModel.copy(password = password, isPrimary = true)
                                offlineManager.saveUser(userWithPassword)

                                // Sync dashboard and calendars
                                offlineManager.syncDashboardToOffline(currentUser.uid) { syncSuccess ->
                                    if (syncSuccess) {
                                        Log.d(TAG, "Dashboard synced for offline use")
                                    }
                                }

                                // Sync public holidays
                                offlineManager.syncPublicHolidaysForDashboard(currentUser.uid) { _ ->
                                    Log.d(TAG, "Public holidays synced")
                                }
                            }
                        }
                    }
                }
                Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                updateUI(currentUser)
            } else {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Handle offline login
     */
    private suspend fun loginOffline(email: String, password: String) {
        val user = offlineManager.validateOfflineLogin(email, password)

        if (user != null) {
            // **SAVE SESSION FOR OFFLINE MODE**
            sessionManager.saveSession(
                userId = user.userId,
                email = user.email ?: email,
                isOffline = true
            )

            Toast.makeText(
                this,
                "Logged in offline as ${user.firstName ?: user.email}",
                Toast.LENGTH_SHORT
            ).show()

            updateUIOffline(user)
        } else {
            Toast.makeText(
                this,
                "Offline login failed. Please connect to internet to login with this account.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Navigate to HomeActivity (online mode)
     */
    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            Log.d(TAG, "User is null, staying on LoginActivity")
        }
    }

    /**
     * Navigate to HomeActivity (offline mode)
     */
    private fun updateUIOffline(user: UserModel?) {
        if (user != null) {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            Log.d(TAG, "User is null, staying on LoginActivity")
        }
    }
}
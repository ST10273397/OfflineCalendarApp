package com.example.prog7314progpoe.ui.reglogin

import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.user.FirebaseUserDbHelper
import com.example.prog7314progpoe.database.user.UserModel
import com.example.prog7314progpoe.offline.OfflineManager
import com.example.prog7314progpoe.offline.SessionManager
import com.example.prog7314progpoe.ui.HomeActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class AuthActivity : AppCompatActivity() {

    // -------------------------
    // Firebase & Authentication
    // -------------------------
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001 // Request code for Google sign-in

    private val db = FirebaseDatabase
        .getInstance("https://chronosync-f3425-default-rtdb.europe-west1.firebasedatabase.app/")
        .getReference("users")

    // Credential Manager (optional Google sign-in)
    private val credentialManager by lazy { CredentialManager.create(this) }
    private val googleIdOption by lazy {
        com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
            .setServerClientId(getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .build()
    }
    private val request by lazy {
        GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    // -------------------------
    // Biometric login
    // -------------------------
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    // -------------------------
    // Offline session & storage
    // -------------------------
    private lateinit var sessionManager: SessionManager

    // -------------------------
    // Lifecycle
    // -------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        // -------------------------
        // Theme initialization (dark mode)
        // -------------------------
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val darkModeEnabled = prefs.getBoolean("dark_mode", false)
        val desiredMode = if (darkModeEnabled) AppCompatDelegate.MODE_NIGHT_YES
        else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != desiredMode) {
            AppCompatDelegate.setDefaultNightMode(desiredMode)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        // -------------------------
        // Firebase auth & session setup
        // -------------------------
        auth = FirebaseAuth.getInstance()
        sessionManager = SessionManager(this)

        // -------------------------
        // Biometric setup
        // -------------------------
        executor = ContextCompat.getMainExecutor(this)
        setupBiometricPrompt()

        val biometricLoginButton = findViewById<LinearLayout>(R.id.btnBiometric_login)
        biometricLoginButton.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }

        // -------------------------
        // UI references
        // -------------------------
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnSignUp = findViewById<Button>(R.id.btnSignUp)
        val googleLoginBtn = findViewById<LinearLayout>(R.id.btnGoogleLogin)

        // -------------------------
        // Navigation for login/register
        // -------------------------
        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        btnSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // -------------------------
        // Google Sign-In setup
        // -------------------------
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Handle Google login button click
        googleLoginBtn.setOnClickListener {
            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
        }
    }

    // -------------------------
    // Biometric prompt configuration
    // -------------------------
    private fun setupBiometricPrompt() {
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    handleBiometricSuccess()
                }

                override fun onAuthenticationFailed() {
                    Toast.makeText(applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Use account password")
            .build()
    }

    // -------------------------
    // Biometric login handling
    // -------------------------
    private fun handleBiometricSuccess() {
        lifecycleScope.launch {
            val offlineManager = OfflineManager(this@AuthActivity)
            val primaryUser = offlineManager.getPrimaryUser()

            if (primaryUser == null) {
                Toast.makeText(this@AuthActivity, "No biometric user configured. Login normally first.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val email = primaryUser.email?.trim().orEmpty()
            val password = primaryUser.password?.trim().orEmpty()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this@AuthActivity, "Stored credentials invalid.", Toast.LENGTH_LONG).show()
                return@launch
            }

            if (offlineManager.isOnline()) {
                // Online: authenticate with Firebase
                FirebaseUserDbHelper.loginUser(email, password) { success, message ->
                    if (success) {
                        sessionManager.saveSession(primaryUser.userId, email, isOffline = false)
                        Toast.makeText(this@AuthActivity, "Logged in with biometrics", Toast.LENGTH_SHORT).show()
                        lifecycleScope.launch { offlineManager.syncDashboardToOffline(primaryUser.userId) {} }
                        updateUI(auth.currentUser)
                    } else {
                        Toast.makeText(this@AuthActivity, "Biometric login failed: $message", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // Offline: use cached credentials
                sessionManager.saveSession(primaryUser.userId, email, isOffline = true)
                Toast.makeText(this@AuthActivity, "Offline mode - using biometrics", Toast.LENGTH_SHORT).show()
                updateUIOffline(primaryUser)
            }
        }
    }

    // -------------------------
    // Google Sign-In result
    // -------------------------
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -------------------------
    // Firebase authentication with Google
    // -------------------------
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                if (user != null) createUserInDatabaseIfNotExists(user)
                updateUI(user)
            } else {
                Log.w(ContentValues.TAG, "signInWithCredential:failure", task.exception)
                updateUI(null)
            }
        }
    }

    // -------------------------
    // Handle Credential Manager Google sign-in
    // -------------------------
    private fun handleSignIn(credential: Credential) {
        if (credential is CustomCredential && credential.type ==
            com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.createFrom(credential.data)
            firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
        } else {
            Log.w(ContentValues.TAG, "Credential is not Google ID Token type")
        }
    }

    // -------------------------
    // UI updates
    // -------------------------
    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        } else Log.d(ContentValues.TAG, "User is null, staying on LoginActivity")
    }

    private fun updateUIOffline(user: UserModel?) {
        if (user != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        } else Log.d(ContentValues.TAG, "User is null, staying on LoginActivity")
    }

    // -------------------------
    // Ensure user exists in Realtime Database
    // -------------------------
    private fun createUserInDatabaseIfNotExists(user: FirebaseUser, onComplete: (() -> Unit)? = null) {
        db.child(user.uid).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                val newUser = UserModel(
                    userId = user.uid,
                    email = user.email ?: "",
                    firstName = user.displayName ?: "",
                    lastName = "",
                    location = "",
                    dateOfBirth = null
                )
                db.child(user.uid).setValue(newUser).addOnCompleteListener { onComplete?.invoke() }
            } else {
                onComplete?.invoke()
            }
        }
    }
}

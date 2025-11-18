package com.example.prog7314progpoe.ui.reglogin

import android.content.Intent
import android.os.Bundle
import android.util.Log  // ← ADD THIS LINE
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.prog7314progpoe.ui.HomeActivity
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.meeting.FirebaseMeetingDbHelper
import com.example.prog7314progpoe.database.user.FirebaseUserDbHelper
import com.google.firebase.auth.FirebaseAuth

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val firstNameEt = findViewById<EditText>(R.id.etFirstName)
        val surnameEt = findViewById<EditText>(R.id.etSurname)
        val dateOfBirthEt = findViewById<EditText>(R.id.etDateOfBirth)
        val locationEt = findViewById<EditText>(R.id.etLocation)
        val emailEt = findViewById<EditText>(R.id.etEmail)
        val passwordEt = findViewById<EditText>(R.id.etPassword)
        val confirmPassEt = findViewById<EditText>(R.id.etConfirmPassword)
        val signUpBtn = findViewById<Button>(R.id.btnSignUp)
        val signInLink = findViewById<TextView>(R.id.tvSignIn)

        signUpBtn.setOnClickListener {
            val firstName = firstNameEt.text.toString().trim()
            val surname = surnameEt.text.toString().trim()
            val dob = dateOfBirthEt.text.toString().trim()
            val location = locationEt.text.toString().trim()
            val email = emailEt.text.toString().trim()
            val password = passwordEt.text.toString().trim()
            val confirmPass = confirmPassEt.text.toString().trim()

            if (firstName.isBlank() || surname.isBlank() || email.isBlank() ||
                password.isBlank() || confirmPass.isBlank()
            ) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPass) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            FirebaseUserDbHelper.registerUser(email, password, firstName, surname, dob, location) { success, message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                if (success) {
                    Log.d("RegisterActivity", "Registration successful")

                    // Navigate immediately, calendar will be created in background
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()

                    // Create meetings calendar in background (non-blocking)
                    val userId = FirebaseAuth.getInstance().currentUser?.uid
                    if (userId != null) {
                        FirebaseMeetingDbHelper.ensureMeetingCalendar(userId) { calendarId ->
                            Log.d("RegisterActivity", "Meeting calendar created: $calendarId")
                        }
                    }
                }
            }
        }

        signInLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}
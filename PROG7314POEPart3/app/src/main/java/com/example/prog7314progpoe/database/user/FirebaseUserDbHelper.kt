package com.example.prog7314progpoe.database.user

import com.example.prog7314progpoe.database.holidays.HolidayModel.DateInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Helper object for managing Firebase Realtime Database operations for users.
 * Includes registration, login, retrieving users, and managing user dashboards.
 */
object FirebaseUserDbHelper {

    private const val RTDB_URL = "https://chronosync-f3425-default-rtdb.europe-west1.firebasedatabase.app/"

    // References to Firebase Database paths
    private val db = FirebaseDatabase.getInstance(RTDB_URL).getReference("users")
    private val calendarRef = FirebaseDatabase.getInstance(RTDB_URL).getReference("calendars")

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // -------------------------------
    // User Authentication & Registration
    // -------------------------------

    /**
     * Registers a new user with email and password, and stores additional user data in Firebase.
     *
     * @param email User email
     * @param password User password
     * @param firstName User first name
     * @param lastName User last name
     * @param dateOfBirth User date of birth in ISO format
     * @param location User location
     * @param onComplete Callback with success flag and message
     */
    fun registerUser(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        dateOfBirth: String,
        location: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val cleanEmail = email.trim().lowercase()

        auth.createUserWithEmailAndPassword(cleanEmail, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
                    val user = UserModel(
                        userId = uid,
                        email = cleanEmail,
                        firstName = firstName,
                        lastName = lastName,
                        location = location,
                        dateOfBirth = DateInfo(dateOfBirth)
                    )
                    db.child(uid).setValue(user)
                        .addOnSuccessListener { onComplete(true, "Registration successful") }
                        .addOnFailureListener { ex -> onComplete(false, "Error: ${ex.message}") }
                } else {
                    onComplete(false, "Registration failed: ${task.exception?.message}")
                }
            }
    }

    /**
     * Logs in a user using email and password.
     *
     * @param email User email
     * @param password User password
     * @param onComplete Callback with success flag and message
     */
    fun loginUser(
        email: String?,
        password: String?,
        onComplete: (Boolean, String) -> Unit
    ) {
        val cleanEmail = email?.trim()?.lowercase().orEmpty()
        val cleanPassword = password?.trim().orEmpty()

        if (cleanEmail.isBlank() || cleanPassword.isBlank()) {
            onComplete(false, "Missing or invalid credentials")
            return
        }

        auth.signInWithEmailAndPassword(cleanEmail, cleanPassword)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) onComplete(true, "Login successful")
                else onComplete(false, "Login failed: ${task.exception?.message}")
            }
    }

    // -------------------------------
    // User Data Retrieval
    // -------------------------------

    /**
     * Fetches a single user by userId.
     *
     * @param userId Firebase UID of the user
     * @param callback Returns UserModel if found, null otherwise
     */
    fun getUser(userId: String, callback: (UserModel?) -> Unit) {
        db.child(userId).get()
            .addOnSuccessListener { snapshot -> callback(snapshot.getValue(UserModel::class.java)) }
            .addOnFailureListener { callback(null) }
    }

    /**
     * Fetches all users from Firebase.
     *
     * @param callback Returns a list of UserModel objects
     */
    fun getAllUsers(callback: (List<UserModel>) -> Unit) {
        db.get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.children.mapNotNull { it.getValue(UserModel::class.java) }
                callback(list)
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    // -------------------------------
    // User Dashboard Management
    // -------------------------------

    /**
     * Adds a calendar to the current user's dashboard.
     * Limits to 8 calendars max.
     *
     * @param calendarId ID of the calendar to add
     * @param onComplete Callback with success flag and message
     */
    fun addCalendarToDashboard(calendarId: String, onComplete: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false, "Not logged in")

        db.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.childrenCount >= 8) {
                    onComplete(false, "You can only have 8 calendars on your dashboard")
                    return
                }

                db.child(uid).child(calendarId).setValue(true)
                    .addOnSuccessListener { onComplete(true, "Added successfully") }
                    .addOnFailureListener { onComplete(false, "Error adding calendar") }
            }

            override fun onCancelled(error: DatabaseError) {
                onComplete(false, "Error: ${error.message}")
            }
        })
    }

    /**
     * Removes a calendar from the current user's dashboard.
     *
     * @param calendarId ID of the calendar to remove
     * @param onComplete Callback with success flag
     */
    fun removeCalendarFromDashboard(calendarId: String, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        db.child(uid).child(calendarId).removeValue()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    /**
     * Fetches all calendar data for the user's dashboard.
     *
     * @param callback Returns a list of maps containing calendar data
     */
    fun getUserDashboardCalendars(callback: (List<Map<String, Any>>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return callback(emptyList())

        db.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val calendarIds = snapshot.children.map { it.key ?: "" }

                if (calendarIds.isEmpty()) {
                    callback(emptyList())
                    return
                }

                val resultList = mutableListOf<Map<String, Any>>()
                var remaining = calendarIds.size

                for (id in calendarIds) {
                    calendarRef.child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(calendarSnap: DataSnapshot) {
                            calendarSnap.value?.let { value ->
                                if (value is Map<*, *>) {
                                    @Suppress("UNCHECKED_CAST")
                                    resultList.add(value as Map<String, Any>)
                                }
                            }
                            remaining--
                            if (remaining == 0) callback(resultList)
                        }

                        override fun onCancelled(error: DatabaseError) {
                            remaining--
                            if (remaining == 0) callback(resultList)
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                callback(emptyList())
            }
        })
    }
}

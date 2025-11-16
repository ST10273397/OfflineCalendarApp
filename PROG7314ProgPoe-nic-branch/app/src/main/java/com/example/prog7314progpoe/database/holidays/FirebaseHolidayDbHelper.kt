package com.example.prog7314progpoe.database.holidays

import com.google.firebase.database.FirebaseDatabase

object FirebaseHolidayDbHelper {

    private const val RTDB_URL =
        "https://chronosync-f3425-default-rtdb.europe-west1.firebasedatabase.app/"

    private val db = FirebaseDatabase.getInstance(RTDB_URL).reference

    // Add a holiday to a calendar
    fun addHoliday(
        calendarId: String?,
        holiday: HolidayModel,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (calendarId.isNullOrBlank()) {
            onError("Missing calendarId.")
            return
        }

        val holidaysRef = db.child("calendars").child(calendarId).child("holidays")
        val id = holiday.holidayId?.takeIf { it.isNotBlank() }
            ?: holidaysRef.push().key

        if (id.isNullOrBlank()) {
            onError("Failed to generate holiday id.")
            return
        }

        holiday.holidayId = id
        holidaysRef.child(id)
            .setValue(holiday)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Failed to save holiday.") }
    }

    // Update an existing holiday
    fun updateHoliday(
        calendarId: String,
        holiday: HolidayModel,
        onComplete: (Boolean) -> Unit
    ) {
        if (holiday.holidayId.isNullOrEmpty()) {
            onComplete(false)
            return
        }

        db.child("calendars").child(calendarId).child("holidays").child(holiday.holidayId!!)
            .setValue(holiday)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    // Delete a holiday from a calendar
    fun deleteHoliday(
        calendarId: String,
        holidayId: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        db.child("calendars").child(calendarId).child("holidays").child(holidayId)
            .removeValue()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    // Get all holidays for a specific calendar
    fun getAllHolidays(
        calendarId: String,
        callback: (List<HolidayModel>) -> Unit
    ) {
        db.child("calendars").child(calendarId).child("holidays").get()
            .addOnSuccessListener { snapshot ->
                val holidays = snapshot.children.mapNotNull { it.getValue(HolidayModel::class.java) }
                callback(holidays)
            }
            .addOnFailureListener { callback(emptyList()) }
    }
}

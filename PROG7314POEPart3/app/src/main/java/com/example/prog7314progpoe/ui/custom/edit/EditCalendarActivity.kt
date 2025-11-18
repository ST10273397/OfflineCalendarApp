package com.example.prog7314progpoe.ui.custom.edit

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.example.prog7314progpoe.ui.custom.detail.CalendarDetailActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * EditCalendarActivity
 *
 * Simple screen that lets the user pick one of their calendars, edit its title/description,
 * and save the changes back to Firebase. After a successful save the activity navigates
 * to the CalendarDetailActivity for the updated calendar.
 *
 * Improvements in this version:
 *  - clearer comments and structure
 *  - defensive checks to avoid crashes when lists are empty
 *  - disabled save button when no calendar is available
 *  - small logging additions to help debugging
 */
class EditCalendarActivity : AppCompatActivity() {

    private lateinit var calendarSpinner: Spinner
    private lateinit var titleEt: EditText
    private lateinit var descEt: EditText
    private lateinit var saveBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var db: DatabaseReference

    // Local cache of the user's calendars (populated from Firebase)
    private val calendarList: MutableList<CalendarModel> = mutableListOf()

    // The currently selected calendar id (nullable until a selection is made)
    private var selectedCalendarId: String? = null

    companion object {
        private const val TAG = "EditCalendarActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_calendar)

        // Bind UI
        calendarSpinner = findViewById(R.id.spn_Calendar)
        titleEt = findViewById(R.id.et_CalendarTitle)
        descEt = findViewById(R.id.et_CalendarDesc)
        saveBtn = findViewById(R.id.btn_SaveCalendar)
        cancelBtn = findViewById(R.id.btn_Cancel)

        db = FirebaseDatabase.getInstance().reference

        // Require an authenticated user — if missing notify and finish
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "No current user - closing activity")
            finish()
            return
        }

        // Initially disable save until we have at least one calendar to edit
        saveBtn.isEnabled = false

        // Load user's calendars (owned or shared)
        loadUserCalendars(currentUserId)

        // Spinner behaviour: update fields when selection changes
        calendarSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Defensive: ensure position is valid
                if (position < 0 || position >= calendarList.size) {
                    selectedCalendarId = null
                    titleEt.setText("")
                    descEt.setText("")
                    saveBtn.isEnabled = false
                    return
                }

                val selectedCalendar = calendarList[position]
                selectedCalendarId = selectedCalendar.calendarId
                titleEt.setText(selectedCalendar.title.orEmpty())
                descEt.setText(selectedCalendar.description.orEmpty())
                saveBtn.isEnabled = true
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Keep UI in a safe state
                selectedCalendarId = null
                titleEt.setText("")
                descEt.setText("")
                saveBtn.isEnabled = false
            }
        }

        // Save button: validate inputs and update Firebase
        saveBtn.setOnClickListener {
            val id = selectedCalendarId
            val newTitle = titleEt.text.toString().trim()
            val newDesc = descEt.text.toString().trim()

            if (id.isNullOrEmpty()) {
                Toast.makeText(this, "No calendar selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newTitle.isEmpty()) {
                titleEt.error = "Title cannot be empty"
                titleEt.requestFocus()
                return@setOnClickListener
            }

            // Prepare updates map
            val updates = hashMapOf<String, Any?>(
                "title" to newTitle,
                "description" to newDesc
            )

            // Perform update and navigate to detail on success
            db.child("calendars").child(id)
                .updateChildren(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Calendar updated!", Toast.LENGTH_SHORT).show()

                    // Navigate to detail activity for this calendar
                    val i = Intent(this, CalendarDetailActivity::class.java).apply {
                        putExtra(CalendarDetailActivity.EXTRA_CAL_ID, id)
                    }
                    startActivity(i)
                    finish()
                }
                .addOnFailureListener { ex ->
                    Log.e(TAG, "Failed to update calendar $id", ex)
                    Toast.makeText(this, "Update failed: ${ex.message}", Toast.LENGTH_SHORT).show()
                }
        }

        cancelBtn.setOnClickListener { finish() }
    }

    /**
     * Load calendars for the given userId. The method reads the "user_calendars" node
     * to get calendar IDs and then loads each calendar object from the "calendars" node.
     * Results populate [calendarList] and the spinner.
     */
    private fun loadUserCalendars(userId: String) {
        db.child("user_calendars").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    calendarList.clear()

                    // Extract child keys (calendar IDs)
                    val ids = snapshot.children.mapNotNull { it.key }
                    if (ids.isEmpty()) {
                        updateSpinner()
                        return
                    }

                    var remaining = ids.size
                    ids.forEach { id ->
                        db.child("calendars").child(id)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(calSnap: DataSnapshot) {
                                    calSnap.getValue(CalendarModel::class.java)?.let { cal ->
                                        // Ensure calendarId is set from the snapshot key
                                        cal.calendarId = calSnap.key.orEmpty()
                                        calendarList.add(cal)
                                    }

                                    // When all lookups finish update the spinner
                                    remaining -= 1
                                    if (remaining <= 0) updateSpinner()
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Log.w(TAG, "Loading calendar $id cancelled: ${error.message}")
                                    remaining -= 1
                                    if (remaining <= 0) updateSpinner()
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Loading user_calendars cancelled: ${error.message}")
                    updateSpinner()
                }
            })
    }

    /**
     * Refresh the spinner to display titles for all loaded calendars.
     * If no calendars exist the spinner will be populated with a single "(No calendars)" item
     * and the Save button will be disabled.
     */
    private fun updateSpinner() {
        val names = if (calendarList.isNotEmpty()) calendarList.map { it.title ?: "(Untitled)" }
        else listOf("(No calendars)")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        calendarSpinner.adapter = adapter

        if (calendarList.isNotEmpty()) {
            // If we already had a previously selected id, select its index; otherwise default to first
            val selectedIndex = selectedCalendarId?.let { id -> calendarList.indexOfFirst { it.calendarId == id } }
                ?.takeIf { it >= 0 } ?: 0
            calendarSpinner.setSelection(selectedIndex)
            saveBtn.isEnabled = true
        } else {
            calendarSpinner.setSelection(0)
            saveBtn.isEnabled = false
            // Clear the edit fields to avoid stale values
            titleEt.setText("")
            descEt.setText("")
        }
    }
}

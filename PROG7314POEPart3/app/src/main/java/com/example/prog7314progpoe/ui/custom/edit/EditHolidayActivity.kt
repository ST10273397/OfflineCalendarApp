package com.example.prog7314progpoe.ui.custom.edit

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * EditHolidayActivity
 *
 * Small form that allows editing a single event (holiday) belonging to a user calendar.
 * On success it saves a partial update to Firebase and returns the user to the caller.
 *
 * This cleaned-up version adds clear comments, defensive checks, logging and small UX
 * improvements such as disabling the Save button when nothing is selected.
 */
class EditHolidayActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EditHolidayActivity"
    }

    // UI
    private lateinit var spnCalendar: Spinner
    private lateinit var spnHoliday: Spinner
    private lateinit var etName: TextInputEditText
    private lateinit var etDate: TextInputEditText
    private lateinit var etDesc: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    // Firebase
    private lateinit var db: DatabaseReference

    // Local caches
    private val calendarList = mutableListOf<CalendarModel>()
    private val holidayList = mutableListOf<HolidayModel>()

    // Selected ids
    private var selectedCalendarId: String? = null
    private var selectedHolidayId: String? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_holiday)

        // Back arrow in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.app_name)

        // Bind views
        spnCalendar = findViewById(R.id.spn_Calendar)
        spnHoliday = findViewById(R.id.spn_Holiday)
        etName = findViewById(R.id.et_HolidayName)
        etDate = findViewById(R.id.et_HolidayDate)
        etDesc = findViewById(R.id.et_HolidayDesc)
        btnSave = findViewById(R.id.btn_SaveHoliday)
        btnCancel = findViewById(R.id.btn_Cancel)

        db = FirebaseDatabase.getInstance().reference

        // Ensure user is logged in
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Disable save initially until a holiday is selected
        btnSave.isEnabled = false

        // Load this user's calendars
        loadUserCalendars(uid)

        // When a calendar is chosen, load its holidays
        spnCalendar.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val cal = calendarList.getOrNull(position) ?: run {
                    selectedCalendarId = null
                    // clear holidays and UI
                    holidayList.clear(); updateHolidaySpinner()
                    return
                }
                selectedCalendarId = cal.calendarId
                loadHolidaysForCalendar(cal.calendarId)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedCalendarId = null
                holidayList.clear(); updateHolidaySpinner()
            }
        }

        // When a holiday is chosen, populate the form
        spnHoliday.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val h = holidayList.getOrNull(position) ?: run {
                    selectedHolidayId = null
                    etName.setText(""); etDesc.setText(""); etDate.setText("")
                    btnSave.isEnabled = false
                    return
                }
                selectedHolidayId = h.holidayId
                etName.setText(h.name.orEmpty())
                etDesc.setText(h.desc.orEmpty())
                // Show ISO date when available
                etDate.setText(h.date?.iso.orEmpty())
                btnSave.isEnabled = true
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedHolidayId = null
                etName.setText(""); etDesc.setText(""); etDate.setText("")
                btnSave.isEnabled = false
            }
        }

        // Date picker: prevent choosing past dates
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.now())
            .build()

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Pick date")
            .setCalendarConstraints(constraints)
            .build()

        etDate.setOnClickListener { datePicker.show(supportFragmentManager, "date") }

        datePicker.addOnPositiveButtonClickListener { utcMillis ->
            val localDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            etDate.setText(localDate.toString()) // format: yyyy-MM-dd
        }

        // Save and cancel actions
        btnSave.setOnClickListener { saveEdits() }
        btnCancel.setOnClickListener { finish() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // -------------------------
    // Data loading helpers
    // -------------------------

    /**
     * Loads calendar IDs for the user from 'user_calendars/{userId}' then fetches
     * each calendar at 'calendars/{calendarId}'. Results populate calendarList.
     */
    private fun loadUserCalendars(userId: String) {
        db.child("user_calendars").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    calendarList.clear()
                    val ids = snapshot.children.mapNotNull { it.key }
                    if (ids.isEmpty()) {
                        updateCalendarSpinner()
                        Toast.makeText(this@EditHolidayActivity, "No calendars", Toast.LENGTH_SHORT).show()
                        return
                    }

                    var remaining = ids.size
                    ids.forEach { id ->
                        db.child("calendars").child(id)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(calSnap: DataSnapshot) {
                                    calSnap.getValue(CalendarModel::class.java)?.let { cal ->
                                        cal.calendarId = calSnap.key.orEmpty()
                                        calendarList.add(cal)
                                    }
                                    remaining -= 1
                                    if (remaining <= 0) updateCalendarSpinner()
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Log.w(TAG, "Failed loading calendar $id: ${error.message}")
                                    remaining -= 1
                                    if (remaining <= 0) updateCalendarSpinner()
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Loading user_calendars cancelled: ${error.message}")
                    updateCalendarSpinner()
                }
            })
    }

    private fun updateCalendarSpinner() {
        val names = if (calendarList.isNotEmpty()) calendarList.map { it.title ?: "(Untitled)" } else listOf("(No calendars)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spnCalendar.adapter = adapter
        if (calendarList.isNotEmpty()) {
            spnCalendar.setSelection(0)
        }
    }

    /**
     * Load holiday items for the given calendar (reads 'calendars/{id}/holidays')
     */
    private fun loadHolidaysForCalendar(calendarId: String?) {
        if (calendarId.isNullOrEmpty()) {
            holidayList.clear(); updateHolidaySpinner(); return
        }

        db.child("calendars").child(calendarId).child("holidays")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    holidayList.clear()
                    snap.children.forEach { hSnap ->
                        val h = hSnap.getValue(HolidayModel::class.java)
                        if (h != null) {
                            h.holidayId = hSnap.key.orEmpty()
                            holidayList.add(h)
                        }
                    }
                    updateHolidaySpinner()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Loading holidays cancelled: ${error.message}")
                    holidayList.clear(); updateHolidaySpinner()
                }
            })
    }

    private fun updateHolidaySpinner() {
        val titles = if (holidayList.isNotEmpty()) holidayList.map { it.name ?: "(Untitled event)" } else listOf("(No events)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, titles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spnHoliday.adapter = adapter
        if (holidayList.isNotEmpty()) spnHoliday.setSelection(0)

        if (holidayList.isEmpty()) {
            etName.setText("")
            etDesc.setText("")
            etDate.setText("")
            btnSave.isEnabled = false
        }
    }

    // -------------------------
    // Save
    // -------------------------

    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveEdits() {
        val calId = selectedCalendarId
        val holId = selectedHolidayId
        val title = etName.text?.toString()?.trim().orEmpty()
        val dateStr = etDate.text?.toString()?.trim().orEmpty()
        val desc = etDesc.text?.toString()?.trim().orEmpty()

        // Basic validation
        if (calId.isNullOrEmpty()) { Toast.makeText(this, "Select a calendar", Toast.LENGTH_SHORT).show(); return }
        if (holId.isNullOrEmpty()) { Toast.makeText(this, "Select an event", Toast.LENGTH_SHORT).show(); return }
        if (title.isEmpty()) { etName.error = "Title required"; etName.requestFocus(); return }

        val pickedDate = runCatching { LocalDate.parse(dateStr) }.getOrNull()
        if (pickedDate == null) { etDate.error = "Pick a valid date"; etDate.requestFocus(); return }

        val today = LocalDate.now()
        if (pickedDate.isBefore(today)) { etDate.error = "Date cannot be in the past"; etDate.requestFocus(); return }

        // Partial update map - nested property paths are supported by updateChildren
        val updates = hashMapOf<String, Any?>(
            "name" to title,
            "desc" to desc,
            "date/iso" to pickedDate.toString()
        )

        db.child("calendars").child(calId).child("holidays").child(holId)
            .updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Event updated", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { ex ->
                Log.e(TAG, "Failed to update holiday $holId", ex)
                Toast.makeText(this, "Update failed: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

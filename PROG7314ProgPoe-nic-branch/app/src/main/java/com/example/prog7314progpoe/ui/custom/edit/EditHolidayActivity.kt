/**
Edit form for a single event
the user adjusts the event info
On success it takes the user back to the calendar details
 */

package com.example.prog7314progpoe.ui.custom.edit

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
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

class EditHolidayActivity : AppCompatActivity() {

    // UI
    private lateinit var spnCalendar: Spinner
    private lateinit var spnHoliday: Spinner
    private lateinit var etName: TextInputEditText
    private lateinit var etDate: TextInputEditText
    private lateinit var etDesc: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    // Data
    private lateinit var db: DatabaseReference
    private val calendarList = mutableListOf<CalendarModel>()
    private val holidayList = mutableListOf<HolidayModel>()
    private var selectedCalendarId: String? = null
    private var selectedHolidayId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_holiday)

        // Action bar back arrow
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

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load calendars for this user
        loadUserCalendars(uid)

        // Calendar selection -> load its holidays
        spnCalendar.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val cal = calendarList.getOrNull(position) ?: return
                selectedCalendarId = cal.calendarId
                loadHolidaysForCalendar(cal.calendarId)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Holiday selection -> populate fields
        spnHoliday.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val h = holidayList.getOrNull(position) ?: return
                selectedHolidayId = h.holidayId
                etName.setText(h.name.orEmpty())
                etDesc.setText(h.desc.orEmpty())
                etDate.setText(h.date?.iso.orEmpty())
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Date picker (no past dates)
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.now())
            .build()
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Pick date")
            .setCalendarConstraints(constraints)
            .build()

        etDate.setOnClickListener {
            datePicker.show(supportFragmentManager, "date")
        }
        datePicker.addOnPositiveButtonClickListener { utcMillis ->
            val localDate = Instant.ofEpochMilli(utcMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            etDate.setText(localDate.toString()) // yyyy-MM-dd
        }

        // Save
        btnSave.setOnClickListener { saveEdits() }

        // Cancel
        btnCancel.setOnClickListener { finish() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // -------------------------
    // Data loading
    // -------------------------

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
                                        cal.calendarId = calSnap.key
                                        calendarList.add(cal)
                                    }
                                    if (--remaining == 0) updateCalendarSpinner()
                                }
                                override fun onCancelled(error: DatabaseError) {
                                    if (--remaining == 0) updateCalendarSpinner()
                                }
                            })
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    updateCalendarSpinner()
                }
            })
    }

    private fun updateCalendarSpinner() {
        val names = calendarList.map { it.title ?: "(Untitled)" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spnCalendar.adapter = adapter
        if (calendarList.isNotEmpty()) spnCalendar.setSelection(0)
    }

    private fun loadHolidaysForCalendar(calendarId: String?) {
        if (calendarId.isNullOrEmpty()) {
            holidayList.clear()
            updateHolidaySpinner()
            return
        }
        db.child("calendars").child(calendarId).child("holidays")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    holidayList.clear()
                    snap.children.forEach { hSnap ->
                        val h = hSnap.getValue(HolidayModel::class.java)
                        if (h != null) {
                            h.holidayId = hSnap.key
                            holidayList.add(h)
                        }
                    }
                    updateHolidaySpinner()
                }
                override fun onCancelled(error: DatabaseError) {
                    holidayList.clear()
                    updateHolidaySpinner()
                }
            })
    }

    private fun updateHolidaySpinner() {
        val titles = holidayList.map { it.name ?: "(Untitled event)" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, titles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spnHoliday.adapter = adapter
        if (holidayList.isNotEmpty()) spnHoliday.setSelection(0)
        // Clear fields if none
        if (holidayList.isEmpty()) {
            etName.setText("")
            etDesc.setText("")
            etDate.setText("")
        }
    }

    // -------------------------
    // Save
    // -------------------------

    private fun saveEdits() {
        val calId = selectedCalendarId
        val holId = selectedHolidayId
        val title = etName.text?.toString()?.trim().orEmpty()
        val dateStr = etDate.text?.toString()?.trim().orEmpty()
        val desc = etDesc.text?.toString()?.trim().orEmpty()

        if (calId.isNullOrEmpty()) {
            Toast.makeText(this, "Select a calendar", Toast.LENGTH_SHORT).show()
            return
        }
        if (holId.isNullOrEmpty()) {
            Toast.makeText(this, "Select an event", Toast.LENGTH_SHORT).show()
            return
        }
        if (title.isEmpty()) {
            etName.error = "Title required"
            etName.requestFocus()
            return
        }
        val pickedDate = runCatching { LocalDate.parse(dateStr) }.getOrNull()
        if (pickedDate == null) {
            etDate.error = "Pick a valid date"
            etDate.requestFocus()
            return
        }
        val today = LocalDate.now()
        if (pickedDate.isBefore(today)) {
            etDate.error = "Date cannot be in the past"
            etDate.requestFocus()
            return
        }

        // Build partial update (keep other fields)
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
            .addOnFailureListener {
                Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
            }
    }
}
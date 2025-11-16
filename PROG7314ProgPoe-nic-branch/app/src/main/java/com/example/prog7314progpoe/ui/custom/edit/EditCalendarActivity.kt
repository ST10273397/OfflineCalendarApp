/**
 * A simple screen for changing a calendars info
 * Save updates the calendar and returns user to its details
 */

package com.example.prog7314progpoe.ui.custom.edit

import android.content.Intent
import android.os.Bundle
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

class EditCalendarActivity : AppCompatActivity() {

    private lateinit var calendarSpinner: Spinner
    private lateinit var titleEt: EditText
    private lateinit var descEt: EditText
    private lateinit var saveBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var db: DatabaseReference

    private val calendarList: MutableList<CalendarModel> = mutableListOf()
    private var selectedCalendarId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_calendar)

        // Bind views
        calendarSpinner = findViewById(R.id.spn_Calendar)
        titleEt = findViewById(R.id.et_CalendarTitle)
        descEt = findViewById(R.id.et_CalendarDesc)
        saveBtn = findViewById(R.id.btn_SaveCalendar)
        cancelBtn = findViewById(R.id.btn_Cancel)

        db = FirebaseDatabase.getInstance().reference
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Load calendars owned/shared to this user
        loadUserCalendars(currentUserId)

        calendarSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val selectedCalendar = calendarList[position]
                selectedCalendarId = selectedCalendar.calendarId
                titleEt.setText(selectedCalendar.title.orEmpty())
                descEt.setText(selectedCalendar.description.orEmpty())
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

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

            val updates = hashMapOf<String, Any?>(
                "title" to newTitle,
                "description" to newDesc
            )

            db.child("calendars").child(id)
                .updateChildren(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Calendar updated!", Toast.LENGTH_SHORT).show()
                    // Go back to the calendar detail view
                    val i = Intent(this, CalendarDetailActivity::class.java)
                    i.putExtra(CalendarDetailActivity.Companion.EXTRA_CAL_ID, id)
                    startActivity(i)
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Update failed!", Toast.LENGTH_SHORT).show()
                }
        }

        cancelBtn.setOnClickListener { finish() }
    }

    private fun loadUserCalendars(userId: String) {
        db.child("user_calendars").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    calendarList.clear()
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
                                        cal.calendarId = calSnap.key
                                        calendarList.add(cal)
                                    }
                                    if (--remaining == 0) updateSpinner()
                                }
                                override fun onCancelled(error: DatabaseError) {
                                    if (--remaining == 0) updateSpinner()
                                }
                            })
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    updateSpinner()
                }
            })
    }

    private fun updateSpinner() {
        val names = calendarList.map { it.title ?: "(Untitled)" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        calendarSpinner.adapter = adapter
        if (calendarList.isNotEmpty()) calendarSpinner.setSelection(0)
    }
}
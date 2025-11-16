/**
A confirmation screen that states which calendar will be removed and offers Delete or “Cancel
On delete it returns the user to the list of calendars
 */

package com.example.prog7314progpoe.ui.custom.edit

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class DeleteCalendarActivity : AppCompatActivity() {

    private lateinit var calendarSpinner: Spinner
    private lateinit var deleteBtn: Button
    private lateinit var db: DatabaseReference
    private var calendarList: MutableList<CalendarModel> = mutableListOf()
    private var selectedCalendarId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delete_calendar)

        calendarSpinner = findViewById(R.id.spn_Calendar)
        deleteBtn = findViewById(R.id.btn_DeleteCalendar)
        db = FirebaseDatabase.getInstance().reference
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        loadUserCalendars(currentUserId)

        calendarSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCalendarId = calendarList[position].calendarId
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        deleteBtn.setOnClickListener {
            if (selectedCalendarId != null) {
                AlertDialog.Builder(this)
                    .setTitle("Delete Calendar")
                    .setMessage("Are you sure you want to delete this calendar?")
                    .setPositiveButton("Yes") { _, _ ->
                        db.child("calendars").child(selectedCalendarId!!).removeValue()
                        Toast.makeText(this, "Calendar deleted", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun loadUserCalendars(userId: String) {
        db.child("user_calendars").child(userId).addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                calendarList.clear()
                val ids = snapshot.children.mapNotNull { it.key }
                ids.forEach { id ->
                    db.child("calendars").child(id).addListenerForSingleValueEvent(object :
                        ValueEventListener {
                        override fun onDataChange(calSnap: DataSnapshot) {
                            val cal = calSnap.getValue(CalendarModel::class.java)
                            if (cal != null) {
                                calendarList.add(cal)
                                updateSpinner()
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateSpinner() {
        val names = calendarList.map { it.title }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        calendarSpinner.adapter = adapter
    }
}
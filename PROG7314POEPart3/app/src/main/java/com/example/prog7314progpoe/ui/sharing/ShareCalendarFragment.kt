package com.example.prog7314progpoe.ui.sharing

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ShareCalendarFragment : Fragment(R.layout.fragment_shared) {

    private lateinit var emailInput: EditText
    private lateinit var calendarSpinner: Spinner
    private lateinit var shareBtn: Button

    private val calendars = mutableListOf<CalendarModel>()
    private lateinit var db: DatabaseReference
    private var currentUserId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emailInput = view.findViewById(R.id.et_Email) // make sure to add this EditText in XML
        calendarSpinner = view.findViewById(R.id.spn_CalendarShare)
        shareBtn = view.findViewById(R.id.btn_Share)

        currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(requireContext(), "User not logged in!", Toast.LENGTH_SHORT).show()
            return
        }

        db = FirebaseDatabase.getInstance().reference

        loadUserCalendars(currentUserId!!)

        shareBtn.setOnClickListener {
            val targetEmail = emailInput.text.toString().trim()
            if (targetEmail.isEmpty()) {
                Toast.makeText(requireContext(), "Enter a user's email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedPos = calendarSpinner.selectedItemPosition
            if (selectedPos < 0 || selectedPos >= calendars.size) {
                Toast.makeText(requireContext(), "Select a calendar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedCalendar = calendars[selectedPos]

            findUserByEmail(targetEmail) { targetUserId ->
                if (targetUserId != null) {
                    shareCalendar(selectedCalendar.calendarId, targetUserId)
                    Toast.makeText(
                        requireContext(),
                        "Shared '${selectedCalendar.title}' with $targetEmail",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadUserCalendars(userId: String) {
        db.child("user_calendars").child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                calendars.clear()
                val calendarIds = snapshot.children.mapNotNull { it.key }

                // Load each calendar object
                calendarIds.forEach { calId ->
                    db.child("calendars").child(calId).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(calSnap: DataSnapshot) {
                            val calendar = calSnap.getValue(CalendarModel::class.java)
                            calendar?.let {
                                calendars.add(it)
                                updateCalendarSpinner()
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateCalendarSpinner() {
        val names = calendars.map { it.title }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        calendarSpinner.adapter = adapter
    }

    private fun findUserByEmail(email: String, callback: (userId: String?) -> Unit) {
        db.child("users").orderByChild("email").equalTo(email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userId = snapshot.children.firstOrNull()?.key
                    callback(userId)
                }
                override fun onCancelled(error: DatabaseError) {
                    callback(null)
                }
            })
    }

    private fun shareCalendar(calendarId: String?, targetUserId: String) {
        if (calendarId != null) {
            db.child("calendars").child(calendarId).child("sharedWith").child(targetUserId).setValue(true)
        }
        if (calendarId != null) {
            db.child("user_calendars").child(targetUserId).child(calendarId).setValue(true)
        }
    }
}

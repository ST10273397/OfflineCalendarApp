/**
 * Entry point for the Custom Calendars area
 * Its the frame that launches the list and routes into detail and edit screens
 */

package com.example.prog7314progpoe.ui.custom.shell

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.FirebaseCalendarDbHelper
import com.example.prog7314progpoe.ui.custom.detail.CalendarDetailActivity
import com.google.firebase.auth.FirebaseAuth

class CustomCalendarActivity : AppCompatActivity() {

    private lateinit var titleEt: EditText
    private lateinit var descEt: EditText
    private lateinit var createBtn: Button
    private lateinit var cancelBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_calendar)

        titleEt = findViewById(R.id.et_Title)
        descEt = findViewById(R.id.et_desc)
        createBtn = findViewById(R.id.btn_update)
        cancelBtn = findViewById(R.id.btn_cancel)

        createBtn.setOnClickListener {
            val title = titleEt.text.toString().trim()
            val desc = descEt.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(this, "Title required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            FirebaseCalendarDbHelper.insertCalendar(
                ownerId = uid,
                title = title,
                description = desc
            ) { newId ->
                if (newId != null) {
                    Toast.makeText(this, "Calendar created", Toast.LENGTH_SHORT).show()
                    val i = Intent(this, CalendarDetailActivity::class.java)
                    i.putExtra(CalendarDetailActivity.Companion.EXTRA_CAL_ID, newId)
                    startActivity(i)
                    finish()
                } else {
                    Toast.makeText(this, "Failed to create", Toast.LENGTH_SHORT).show()
                }
            }
        }

        cancelBtn.setOnClickListener { finish() }
    }
}
package com.example.prog7314progpoe.ui.meetings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.meeting.FirebaseMeetingDbHelper
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class CreateMeetingActivity : AppCompatActivity() {

    private lateinit var inTitle: EditText
    private lateinit var inDescription: EditText
    private lateinit var inDate: EditText
    private lateinit var inTimeStart: EditText
    private lateinit var inTimeEnd: EditText
    private lateinit var inEmails: EditText
    private lateinit var btnCreate: Button
    private lateinit var btnCancel: Button
    private lateinit var progress: ProgressBar

    private val auth by lazy { FirebaseAuth.getInstance() }

    private var selectedDate: Calendar = Calendar.getInstance()
    private var startTime: Long = 0
    private var endTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_meeting)

        inTitle = findViewById(R.id.inTitle)
        inDescription = findViewById(R.id.inDescription)
        inDate = findViewById(R.id.inDate)
        inTimeStart = findViewById(R.id.inTimeStart)
        inTimeEnd = findViewById(R.id.inTimeEnd)
        inEmails = findViewById(R.id.inEmails)
        btnCreate = findViewById(R.id.btnCreate)
        btnCancel = findViewById(R.id.btnCancel)
        progress = findViewById(R.id.progress)

        inDate.setOnClickListener { showDatePicker() }
        inTimeStart.setOnClickListener { showStartTimePicker() }
        inTimeEnd.setOnClickListener { showEndTimePicker() }
        btnCreate.setOnClickListener { createMeeting() }
        btnCancel.setOnClickListener { finish() }
    }

    private fun showDatePicker() {
        val picker = DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate.set(year, month, day)
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                inDate.setText(format.format(selectedDate.time))
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        )
        picker.show()
    }

    private fun showStartTimePicker() {
        val hour = selectedDate.get(Calendar.HOUR_OF_DAY)
        val minute = selectedDate.get(Calendar.MINUTE)

        val picker = TimePickerDialog(
            this,
            { _, h, m ->
                val cal = selectedDate.clone() as Calendar
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, m)
                cal.set(Calendar.SECOND, 0)
                startTime = cal.timeInMillis

                val format = SimpleDateFormat("HH:mm", Locale.getDefault())
                inTimeStart.setText(format.format(Date(startTime)))
            },
            hour,
            minute,
            true
        )
        picker.show()
    }

    private fun showEndTimePicker() {
        val hour = selectedDate.get(Calendar.HOUR_OF_DAY)
        val minute = selectedDate.get(Calendar.MINUTE)

        val picker = TimePickerDialog(
            this,
            { _, h, m ->
                val cal = selectedDate.clone() as Calendar
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, m)
                cal.set(Calendar.SECOND, 0)
                endTime = cal.timeInMillis

                val format = SimpleDateFormat("HH:mm", Locale.getDefault())
                inTimeEnd.setText(format.format(Date(endTime)))
            },
            hour,
            minute,
            true
        )
        picker.show()
    }

    private fun createMeeting() {
        val title = inTitle.text.toString().trim()
        val description = inDescription.text.toString().trim()
        val dateText = inDate.text.toString().trim()
        val emailsText = inEmails.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_enter_meeting_title), Toast.LENGTH_SHORT).show()
            inTitle.requestFocus()
            return
        }

        if (dateText.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_select_date), Toast.LENGTH_SHORT).show()
            return
        }

        if (startTime == 0L) {
            Toast.makeText(this, getString(R.string.toast_select_start_time), Toast.LENGTH_SHORT).show()
            return
        }

        if (endTime == 0L) {
            Toast.makeText(this, getString(R.string.toast_select_end_time), Toast.LENGTH_SHORT).show()
            return
        }

        if (endTime <= startTime) {
            Toast.makeText(this, getString(R.string.toast_end_time_after_start), Toast.LENGTH_SHORT).show()
            return
        }

        if (emailsText.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_enter_participant_email), Toast.LENGTH_SHORT).show()
            inEmails.requestFocus()
            return
        }

        val emails = emailsText.split("[,;]".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (emails.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_enter_valid_emails), Toast.LENGTH_SHORT).show()
            return
        }

        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, getString(R.string.toast_not_logged_in), Toast.LENGTH_SHORT).show()
            return
        }

        progress.visibility = android.view.View.VISIBLE
        btnCreate.isEnabled = false

        FirebaseMeetingDbHelper.createMeeting(
            creatorId = currentUserId,
            title = title,
            description = description,
            dateIso = dateText,
            timeStart = startTime,
            timeEnd = endTime,
            participantEmails = emails,
            onSuccess = { meetingId ->
                progress.visibility = android.view.View.GONE
                Toast.makeText(this, getString(R.string.toast_meeting_created), Toast.LENGTH_SHORT).show()
                finish()
            },
            onError = { errorMessage ->
                progress.visibility = android.view.View.GONE
                btnCreate.isEnabled = true

                if (errorMessage.contains("not found")) {
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, getString(R.string.error_generic, errorMessage), Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}
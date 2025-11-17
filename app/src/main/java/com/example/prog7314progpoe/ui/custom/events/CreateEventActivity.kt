/**
 * A form for adding a new event to the current calendar
 * the user fills in name, date, and type
 * Save adds it and returns to the calendar details
 */

package com.example.prog7314progpoe.ui.custom.events

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.example.prog7314progpoe.database.holidays.FirebaseHolidayDbHelper
import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.example.prog7314progpoe.ui.custom.detail.CalendarDetailActivity
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class CreateEventActivity : AppCompatActivity() {

    private lateinit var calendarSpinner: Spinner
    private lateinit var repeatsSpinner: Spinner
    private lateinit var db: DatabaseReference

    private lateinit var titleEt: EditText
    private lateinit var dateEt: EditText
    private lateinit var startEt: EditText
    private lateinit var endEt: EditText
    private lateinit var descEt: EditText
    private lateinit var createBtn: Button
    private lateinit var resetBtn: Button
    private lateinit var cancelBtn: Button

    private val calendarList: MutableList<CalendarModel> = mutableListOf()
    private var calendarsLoaded = false

    private var pickedLocalDate: LocalDate? = null
    private var pickedStart: LocalTime? = null
    private var pickedEnd: LocalTime? = null

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_event)

        calendarSpinner = findViewById(R.id.spn_calendar)
        repeatsSpinner = findViewById(R.id.spn_Repeats)
        titleEt = findViewById(R.id.et_EventTitle)
        dateEt = findViewById(R.id.et_Date)
        startEt = findViewById(R.id.et_StartTime)
        endEt = findViewById(R.id.et_EndTime)
        descEt = findViewById(R.id.et_Desc)
        createBtn = findViewById(R.id.btn_Create)
        resetBtn = findViewById(R.id.btn_Reset)
        cancelBtn = findViewById(R.id.btn_Cancel)

        db = FirebaseDatabase.getInstance().reference
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Load calendars for this user
        loadUserCalendars(uid)

        // Repeats options
        val repeatOptions = listOf("None", "Daily", "Weekly", "Monthly", "Annually")
        repeatsSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, repeatOptions)

        // --- Date picker (no past dates) ---
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.from(MaterialDatePicker.todayInUtcMilliseconds()))
            .build()

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Pick date")
            .setCalendarConstraints(constraints)
            .build()

        dateEt.setOnClickListener { datePicker.show(supportFragmentManager, "date") }
        datePicker.addOnPositiveButtonClickListener { utcMillis ->
            val ld = Instant.ofEpochMilli(utcMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            pickedLocalDate = ld
            dateEt.setText(ld.format(dateFormat))
        }

        // --- Time pickers ---
        val startPicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setTitleText("Start time")
            .build()

        val endPicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setTitleText("End time")
            .build()

        startEt.setOnClickListener { startPicker.show(supportFragmentManager, "start") }
        endEt.setOnClickListener { endPicker.show(supportFragmentManager, "end") }

        startPicker.addOnPositiveButtonClickListener {
            pickedStart = LocalTime.of(startPicker.hour, startPicker.minute)
            startEt.setText(pickedStart!!.format(timeFormat))
        }
        endPicker.addOnPositiveButtonClickListener {
            pickedEnd = LocalTime.of(endPicker.hour, endPicker.minute)
            endEt.setText(pickedEnd!!.format(timeFormat))
        }

        createBtn.setOnClickListener { createEvent() }
        resetBtn.setOnClickListener { resetFields() }
        cancelBtn.setOnClickListener { finish() }
    }

    private fun resetFields() {
        titleEt.text.clear()
        dateEt.text.clear(); pickedLocalDate = null
        startEt.text.clear(); pickedStart = null
        endEt.text.clear(); pickedEnd = null
        descEt.text.clear()
        repeatsSpinner.setSelection(0)
    }

    private fun createEvent() {
        Log.d("CreateEvent", "Create tapped")

        // Ensure calendars are loaded & selected
        if (!calendarsLoaded || calendarList.isEmpty()) {
            Toast.makeText(this, "Calendars are still loading. Try again in a second.", Toast.LENGTH_SHORT).show()
            return
        }

        // validation
        val title = titleEt.text.toString().trim()
        if (title.isEmpty()) {
            titleEt.error = "Title required"
            titleEt.requestFocus()
            return
        }
        val date = pickedLocalDate ?: run {
            dateEt.error = "Pick a date"
            dateEt.requestFocus()
            return
        }
        if (date.isBefore(LocalDate.now())) {
            dateEt.error = "Date cannot be in the past"
            dateEt.requestFocus()
            return
        }
        val start = pickedStart ?: run {
            startEt.error = "Pick start time"
            startEt.requestFocus()
            return
        }
        val end = pickedEnd ?: run {
            endEt.error = "Pick end time"
            endEt.requestFocus()
            return
        }
        if (!end.isAfter(start)) {
            endEt.error = "End must be after start"
            endEt.requestFocus()
            return
        }

        val pos = calendarSpinner.selectedItemPosition
        if (pos !in calendarList.indices) {
            Toast.makeText(this, "Select a calendar", Toast.LENGTH_SHORT).show()
            return
        }
        val cal = calendarList[pos]
        val calId = cal.calendarId?.trim()
        if (calId.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid calendar. Please reselect.", Toast.LENGTH_SHORT).show()
            Log.w("CreateEvent", "calendarId is null/blank for position $pos, model=$cal")
            return
        }

        // Build payload
        val zone = ZoneId.systemDefault()
        val startMillis = ZonedDateTime.of(date, start, zone).toInstant().toEpochMilli()
        val endMillis   = ZonedDateTime.of(date, end,   zone).toInstant().toEpochMilli()

        val repeatChoice = repeatsSpinner.selectedItem.toString()
        val desc = descEt.text.toString().trim()

        val event = HolidayModel(
            holidayId = null,
            name = title,
            desc = desc,
            date = HolidayModel.DateInfo(date.format(dateFormat)),
            timeStart = startMillis,
            timeEnd = endMillis,
            repeat = if (repeatChoice == "None") emptyList() else listOf(repeatChoice),
            type = listOf("General")
        )

        // Prevent double taps during network
        createBtn.isEnabled = false

        // --- SAVE ---
        FirebaseHolidayDbHelper.addHoliday(
            calId,
            event,
            onSuccess = {
                Toast.makeText(this, "Event created", Toast.LENGTH_SHORT).show()
                val i = Intent(this, CalendarDetailActivity::class.java)
                i.putExtra(CalendarDetailActivity.Companion.EXTRA_CAL_ID, calId)
                startActivity(i)
                finish()
            },
            onError = { msg ->
                createBtn.isEnabled = true
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        )


    }

    private fun loadUserCalendars(userId: String) {
        calendarsLoaded = false
        db.child("user_calendars").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    calendarList.clear()
                    val ids = snapshot.children.mapNotNull { it.key }
                    if (ids.isEmpty()) {
                        calendarsLoaded = true
                        calendarSpinner.adapter = ArrayAdapter(
                            this@CreateEventActivity,
                            android.R.layout.simple_spinner_dropdown_item,
                            listOf("No calendars")
                        )
                        return
                    }

                    var remaining = ids.size
                    ids.forEach { id ->
                        db.child("calendars").child(id)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(calSnap: DataSnapshot) {
                                    calSnap.getValue(CalendarModel::class.java)?.let {
                                        it.calendarId = calSnap.key
                                        calendarList.add(it)
                                    }
                                    if (--remaining == 0) {
                                        calendarsLoaded = true
                                        updateCalendarSpinner()
                                    }
                                }
                                override fun onCancelled(error: DatabaseError) {
                                    if (--remaining == 0) {
                                        calendarsLoaded = true
                                        updateCalendarSpinner()
                                    }
                                }
                            })
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    calendarsLoaded = true
                    updateCalendarSpinner()
                }
            })
    }

    private fun updateCalendarSpinner() {
        val names = if (calendarList.isEmpty()) listOf("No calendars")
        else calendarList.map { it.title ?: "(Untitled)" }

        calendarSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            names
        )

        // Preselect first real calendar if available
        if (calendarList.isNotEmpty()) calendarSpinner.setSelection(0)
    }
}
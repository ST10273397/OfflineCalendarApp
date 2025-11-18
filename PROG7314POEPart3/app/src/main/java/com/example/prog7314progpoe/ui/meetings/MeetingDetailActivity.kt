package com.example.prog7314progpoe.ui.meetings

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.meeting.MeetingModel
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

// shows meeting details and list of participants with their status
class MeetingDetailActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvDateTime: TextView
    private lateinit var rvParticipants: RecyclerView
    private lateinit var progress: ProgressBar

    private val db by lazy {
        FirebaseDatabase.getInstance("https://chronosync-f3425-default-rtdb.europe-west1.firebasedatabase.app/").reference
    }

    private val adapter by lazy { ParticipantsAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting_detail)

        tvTitle = findViewById(R.id.tvTitle)
        tvDescription = findViewById(R.id.tvDescription)
        tvDateTime = findViewById(R.id.tvDateTime)
        rvParticipants = findViewById(R.id.rvParticipants)
        progress = findViewById(R.id.progress)

        rvParticipants.layoutManager = LinearLayoutManager(this)
        rvParticipants.adapter = adapter

        val meetingId = intent.getStringExtra("meetingId") ?: return
        loadMeeting(meetingId)
    }

    private fun loadMeeting(meetingId: String) {
        progress.visibility = android.view.View.VISIBLE

        db.child("meetings/$meetingId").get()
            .addOnSuccessListener { snapshot ->
                val meeting = snapshot.getValue(MeetingModel::class.java)
                if (meeting == null) {
                    Toast.makeText(this, "Meeting not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                tvTitle.text = meeting.title ?: "Untitled Meeting"
                tvDescription.text = meeting.description ?: "No description"

                // format date and time
                val dateStr = meeting.date?.iso ?: ""
                val timeStart = if (meeting.timeStart != null) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(meeting.timeStart!!))
                } else ""
                val timeEnd = if (meeting.timeEnd != null) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(meeting.timeEnd!!))
                } else ""
                tvDateTime.text = "$dateStr from $timeStart to $timeEnd"

                // show participants
                val participants = meeting.participants?.map { (userId, info) ->
                    ParticipantRow(
                        email = info.email ?: "Unknown",
                        status = info.status ?: "pending",
                        isCreator = userId == meeting.creatorId
                    )
                } ?: emptyList()

                adapter.submitList(participants)
                progress.visibility = android.view.View.GONE
            }
            .addOnFailureListener {
                progress.visibility = android.view.View.GONE
                Toast.makeText(this, "Failed to load meeting", Toast.LENGTH_SHORT).show()
            }
    }
}
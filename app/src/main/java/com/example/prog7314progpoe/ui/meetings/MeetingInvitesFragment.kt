package com.example.prog7314progpoe.ui.meetings

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.meeting.FirebaseMeetingDbHelper
import com.example.prog7314progpoe.database.meeting.MeetingModel
import com.google.firebase.auth.FirebaseAuth

// Shows pending meeting invites with accept/decline buttons
class MeetingInvitesFragment : Fragment(R.layout.fragment_meeting_invites) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView

    private lateinit var adapter: MeetingInvitesAdapter
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerInvites)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        progressBar = view.findViewById(R.id.progressBar)
        emptyText = view.findViewById(R.id.emptyText)

        // Setup adapter with accept/decline callbacks
        adapter = MeetingInvitesAdapter(
            onAccept = { meeting -> acceptMeeting(meeting) },
            onDecline = { meeting -> declineMeeting(meeting) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            loadInvites()
        }

        loadInvites()
    }

    override fun onResume() {
        super.onResume()
        loadInvites()
    }

    private fun loadInvites() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            showEmpty("Not logged in")
            return
        }

        showLoading()

        FirebaseMeetingDbHelper.getMeetingInvitesForUser(userId) { meetings ->
            swipeRefresh.isRefreshing = false
            progressBar.visibility = View.GONE

            // Filter for pending only
            val pendingInvites = meetings.filter { meeting ->
                meeting.participants?.get(userId)?.status == "pending"
            }

            if (pendingInvites.isEmpty()) {
                showEmpty("No pending meeting invites")
            } else {
                showInvites(pendingInvites)
            }
        }
    }

    private fun acceptMeeting(meeting: MeetingModel) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(requireContext(), "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val meetingId = meeting.meetingId
        if (meetingId == null) {
            Toast.makeText(requireContext(), "Invalid meeting", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE

        FirebaseMeetingDbHelper.acceptMeeting(
            meetingId = meetingId,
            userId = userId,
            onSuccess = {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Meeting accepted! Check 'My Meetings' tab",
                    Toast.LENGTH_SHORT
                ).show()
                loadInvites()
            },
            onError = { error ->
                progressBar.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Error: $error",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun declineMeeting(meeting: MeetingModel) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(requireContext(), "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val meetingId = meeting.meetingId
        if (meetingId == null) {
            Toast.makeText(requireContext(), "Invalid meeting", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE

        FirebaseMeetingDbHelper.declineMeeting(
            meetingId = meetingId,
            userId = userId,
            onSuccess = {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Meeting declined",
                    Toast.LENGTH_SHORT
                ).show()
                loadInvites()
            },
            onError = { error ->
                progressBar.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Error: $error",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }

    private fun showEmpty(message: String) {
        progressBar.visibility = View.GONE
        emptyText.visibility = View.VISIBLE
        emptyText.text = message
        recyclerView.visibility = View.GONE
        adapter.submitList(emptyList())
    }

    private fun showInvites(invites: List<MeetingModel>) {
        progressBar.visibility = View.GONE
        emptyText.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        adapter.submitList(invites)
    }
}
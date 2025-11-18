package com.example.prog7314progpoe.ui.meetings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.meeting.FirebaseMeetingDbHelper
import com.example.prog7314progpoe.database.meeting.MeetingModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth

/**
 * Fragment showing meetings created by the current user
 */
class MyMeetingsFragment : Fragment(R.layout.fragment_my_meetings) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var fabCreate: FloatingActionButton

    private lateinit var adapter: MeetingsAdapter
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find views
        recyclerView = view.findViewById(R.id.recyclerMeetings)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        progressBar = view.findViewById(R.id.progressBar)
        emptyText = view.findViewById(R.id.emptyText)
        fabCreate = view.findViewById(R.id.fabCreate)

        // Setup RecyclerView
        adapter = MeetingsAdapter { meeting -> openMeetingDetail(meeting) }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Setup FAB - Create new meeting
        fabCreate.setOnClickListener {
            val intent = Intent(requireContext(), CreateMeetingActivity::class.java)
            startActivity(intent)
        }

        // Setup swipe to refresh
        swipeRefresh.setOnRefreshListener {
            loadMeetings()
        }

        // Load meetings
        loadMeetings()
    }

    override fun onResume() {
        super.onResume()
        // Reload meetings when returning to this fragment
        loadMeetings()
    }

    private fun loadMeetings() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            showEmpty("Not logged in")
            return
        }

        showLoading()

        FirebaseMeetingDbHelper.getMeetingsCreatedByUser(userId) { meetings ->
            swipeRefresh.isRefreshing = false
            progressBar.visibility = View.GONE

            if (meetings.isEmpty()) {
                showEmpty("No meetings yet. Tap + to create one!")
            } else {
                showMeetings(meetings)
            }
        }
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

    private fun showMeetings(meetings: List<MeetingModel>) {
        progressBar.visibility = View.GONE
        emptyText.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        adapter.submitList(meetings)
    }

    private fun openMeetingDetail(meeting: MeetingModel) {
        val intent = Intent(requireContext(), MeetingDetailActivity::class.java)
        intent.putExtra("meetingId", meeting.meetingId)
        startActivity(intent)
    }
}
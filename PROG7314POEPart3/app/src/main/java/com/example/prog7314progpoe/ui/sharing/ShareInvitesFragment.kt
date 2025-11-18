package com.example.prog7314progpoe.ui.sharing

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.example.prog7314progpoe.database.calendar.FirebaseCalendarDbHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ShareInvitesFragment : Fragment(R.layout.fragment_share_invites) {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance().reference }

    private lateinit var rv: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView

    private val adapter by lazy {
        ShareInvitesAdapter(
            onAccept = { invite ->
                acceptInvite(invite)
            },
            onDecline = { invite ->
                declineInvite(invite)
            }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rv = view.findViewById(R.id.rvInvites)
        progress = view.findViewById(R.id.progress)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        loadPendingInvites()
    }

    override fun onResume() {
        super.onResume()
        loadPendingInvites()
    }

    private fun loadPendingInvites() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            showEmpty("not signed in")
            return
        }

        progress.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        // Read from user_invites node
        db.child("user_invites").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val calendarIds = snap.children.mapNotNull { it.key }
                    if (calendarIds.isEmpty()) {
                        adapter.submitList(emptyList())
                        showEmpty("no pending invites")
                        return
                    }
                    fetchInviteDetails(calendarIds)
                }

                override fun onCancelled(error: DatabaseError) {
                    adapter.submitList(emptyList())
                    showEmpty(error.message)
                }
            })
    }

    private fun fetchInviteDetails(calendarIds: List<String>) {
        val invites = mutableListOf<InviteRow>()
        var remaining = calendarIds.size

        calendarIds.forEach { calendarId ->
            db.child("calendars").child(calendarId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(calSnap: DataSnapshot) {
                        val calendar = calSnap.getValue(CalendarModel::class.java)
                        if (calendar != null) {
                            val ownerId = calendar.ownerId ?: ""

                            //fetch email
                            db.child("users").child(ownerId)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(userSnap: DataSnapshot) {
                                        val ownerEmail = userSnap.child("email").getValue(String::class.java) ?: "Unknown"

                                        invites.add(InviteRow(
                                            calendarId = calendarId,
                                            title = calendar.title ?: "Untitled",
                                            ownerEmail = ownerEmail
                                        ))

                                        if (--remaining == 0) {
                                            progress.visibility = View.GONE
                                            adapter.submitList(invites.sortedBy { it.title.lowercase() })
                                        }
                                    }

                                    override fun onCancelled(error: DatabaseError) {
                                        if (--remaining == 0) {
                                            progress.visibility = View.GONE
                                            adapter.submitList(invites.sortedBy { it.title.lowercase() })
                                        }
                                    }
                                })
                        } else {
                            if (--remaining == 0) {
                                progress.visibility = View.GONE
                                if (invites.isEmpty()) {
                                    showEmpty("no pending invites")
                                } else {
                                    adapter.submitList(invites.sortedBy { it.title.lowercase() })
                                }
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (--remaining == 0) {
                            progress.visibility = View.GONE
                            showEmpty(error.message)
                        }
                    }
                })
        }
    }

    private fun acceptInvite(invite: InviteRow) {
        val uid = auth.currentUser?.uid ?: return

        progress.visibility = View.VISIBLE

        FirebaseCalendarDbHelper.acceptInvite(
            calendarId = invite.calendarId,
            userId = uid,
            onSuccess = {
                Toast.makeText(requireContext(), "Invite accepted!", Toast.LENGTH_SHORT).show()
                loadPendingInvites() // Refresh list
            },
            onError = { msg ->
                progress.visibility = View.GONE
                Toast.makeText(requireContext(), "Error: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun declineInvite(invite: InviteRow) {
        val uid = auth.currentUser?.uid ?: return

        progress.visibility = View.VISIBLE

        FirebaseCalendarDbHelper.declineInvite(
            calendarId = invite.calendarId,
            userId = uid,
            onSuccess = {
                Toast.makeText(requireContext(), "Invite declined", Toast.LENGTH_SHORT).show()
                loadPendingInvites() // Refresh list
            },
            onError = { msg ->
                progress.visibility = View.GONE
                Toast.makeText(requireContext(), "Error: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun showEmpty(msg: String) {
        progress.visibility = View.GONE
        tvEmpty.text = msg
        tvEmpty.visibility = View.VISIBLE
    }
}

data class InviteRow(
    val calendarId: String,
    val title: String,
    val ownerEmail: String
)
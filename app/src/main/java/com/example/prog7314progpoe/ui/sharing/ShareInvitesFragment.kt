/**
 * share invites fragment
 * shows calendars shared to the current user and lets them leave a calendar
 * uses user_calendars and calendars and users nodes
 */

package com.example.prog7314progpoe.ui.sharing

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ShareInvitesFragment : Fragment(R.layout.fragment_share_invites) {

    //SEGMENT firebase
    //-----------------------------------------------------------------------------------------------
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT views and adapter
    //-----------------------------------------------------------------------------------------------
    private lateinit var rv: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView
    private val adapter by lazy {
        InviteListAdapter(
            onOpen = { row ->
                findNavController().navigate(
                    R.id.inviteDetailFragment,
                    InviteDetailFragment.args(row.calendarId)
                )
            },
            onLeave = { row ->
                leaveCalendar(row.calendarId)
            }
        )
    }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT lifecycle - setup and load
    //-----------------------------------------------------------------------------------------------
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rv = view.findViewById(R.id.rvInvites)
        progress = view.findViewById(R.id.progress)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        loadSharedToMe()
    }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT load - read user_calendars/<uid> then fetch calendars and filter not owned
    //-----------------------------------------------------------------------------------------------
    private fun loadSharedToMe() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            showEmpty("not signed in")
            return
        }

        progress.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        db.child("user_calendars").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(keysSnap: DataSnapshot) {
                    val ids = keysSnap.children.mapNotNull { it.key }
                    if (ids.isEmpty()) {
                        adapter.submitList(emptyList())
                        showEmpty("no shared calendars")
                        return
                    }
                    fetchCalendarsAndOwners(ids, uid)
                }

                override fun onCancelled(error: DatabaseError) {
                    adapter.submitList(emptyList())
                    showEmpty(error.message)
                }
            })
    }

    //SUB-SEGMENT hydrate calendars and owners
    //-----------------------------------------------------------------------------------------------
    private fun fetchCalendarsAndOwners(ids: List<String>, currentUid: String) {
        val rows = mutableListOf<SharedToMeRow>()
        var remaining = ids.size

        ids.forEach { id ->
            db.child("calendars").child(id)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(calSnap: DataSnapshot) {
                        val m = calSnap.getValue(CalendarModel::class.java)
                        if (m != null) {
                            val ownerId = m.ownerId ?: ""
                            val title = m.title ?: ""
                            // only show if I am not the owner
                            if (ownerId.isNotBlank() && ownerId != currentUid) {
                                // fetch owner email then add row
                                db.child("users").child(ownerId).child("email")
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(emailSnap: DataSnapshot) {
                                            val email = emailSnap.getValue(String::class.java) ?: "(owner)"
                                            rows.add(SharedToMeRow(calendarId = id, title = title, ownerEmail = email))
                                            if (--remaining == 0) finishList(rows)
                                        }
                                        override fun onCancelled(error: DatabaseError) {
                                            if (--remaining == 0) finishList(rows)
                                        }
                                    })
                                return
                            }
                        }
                        if (--remaining == 0) finishList(rows)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (--remaining == 0) finishList(rows)
                    }
                })
        }
    }

    //SUB-SEGMENT finish - update ui
    //-----------------------------------------------------------------------------------------------
    private fun finishList(rows: List<SharedToMeRow>) {
        progress.visibility = View.GONE
        if (rows.isEmpty()) {
            adapter.submitList(emptyList())
            tvEmpty.visibility = View.VISIBLE
        } else {
            adapter.submitList(rows.sortedBy { it.title.lowercase() })
            tvEmpty.visibility = View.GONE
        }
    }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT leave - remove my link from sharedWith and user_calendars
    //-----------------------------------------------------------------------------------------------
    private fun leaveCalendar(calendarId: String) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(requireContext(), "not signed in", Toast.LENGTH_SHORT).show()
            return
        }
        val updates = hashMapOf<String, Any?>(
            "calendars/$calendarId/sharedWith/$uid" to null,
            "user_calendars/$uid/$calendarId" to null
        )
        db.updateChildren(updates).addOnCompleteListener { t ->
            if (t.isSuccessful) {
                Toast.makeText(requireContext(), "left calendar", Toast.LENGTH_SHORT).show()
                loadSharedToMe()
            } else {
                Toast.makeText(requireContext(), t.exception?.localizedMessage ?: "leave failed", Toast.LENGTH_LONG).show()
            }
        }
    }
    //-----------------------------------------------------------------------------------------------

    //SUB-SEGMENT helper - show empty
    //-----------------------------------------------------------------------------------------------
    private fun showEmpty(msg: String) {
        progress.visibility = View.GONE
        tvEmpty.text = msg
        tvEmpty.visibility = View.VISIBLE
    }
    //-----------------------------------------------------------------------------------------------
}

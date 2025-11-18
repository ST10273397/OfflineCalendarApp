/**
 * share calendar detail fragment
 * shows the calendar title description and a list of users with access with a remove action
 */

package com.example.prog7314progpoe.ui.sharing

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.fragment.findNavController
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ShareCalendarDetailFragment : Fragment(R.layout.fragment_share_calendar_detail) {

    //SEGMENT args and firebase
    //-----------------------------------------------------------------------------------------------
    private val calendarId: String by lazy {
        requireArguments().getString(ARG_CAL_ID).orEmpty()
    }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT views and adapter
    //-----------------------------------------------------------------------------------------------
    private lateinit var tvTitle: TextView
    private lateinit var tvDesc: TextView
    private lateinit var rvUsers: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView
    private var ownerId: String = ""  // ← Store owner ID

    private val adapter by lazy {
        SharedUsersAdapter(onRemove = { row -> removeUser(row.userId) })
    }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT lifecycle
    //-----------------------------------------------------------------------------------------------
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //SUB-SEGMENT find views
        //-------------------------------------------------
        tvTitle = view.findViewById(R.id.tvTitle)
        tvDesc = view.findViewById(R.id.tvDesc)
        rvUsers = view.findViewById(R.id.rvUsers)
        progress = view.findViewById(R.id.progress)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        //-------------------------------------------------

        //SUB-SEGMENT list setup
        //-------------------------------------------------
        rvUsers.layoutManager = LinearLayoutManager(requireContext())
        rvUsers.adapter = adapter
        //-------------------------------------------------

        //SUB-SEGMENT load header and users
        //-------------------------------------------------
        loadHeader()
        loadUsers()
        //-------------------------------------------------
    }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT header - read title and description from calendars
    //-----------------------------------------------------------------------------------------------
    private fun loadHeader() {
        db.child("calendars").child(calendarId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val m = snap.getValue(CalendarModel::class.java)
                    tvTitle.text = m?.title ?: "(Untitled)"
                    tvDesc.text = m?.description.orEmpty()
                    ownerId = m?.ownerId ?: ""  // ← Store owner ID
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), error.message, Toast.LENGTH_LONG).show()
                }
            })
    }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT users
    //-----------------------------------------------------------------------------------------------
    private fun loadUsers() {
        progress.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        db.child("calendars").child(calendarId).child("sharedWith")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val ids = snap.children.mapNotNull { it.key }
                    if (ids.isEmpty()) {
                        progress.visibility = View.GONE
                        adapter.submitList(emptyList())
                        tvEmpty.visibility = View.VISIBLE
                        return
                    }
                    fetchEmails(ids)
                }

                override fun onCancelled(error: DatabaseError) {
                    progress.visibility = View.GONE
                    adapter.submitList(emptyList())
                    tvEmpty.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), error.message, Toast.LENGTH_LONG).show()
                }
            })
    }

    //SUB-SEGMENT helper - ids to emails
    //-----------------------------------------------------------------------------------------------
    private fun fetchEmails(userIds: List<String>) {
        val rows = mutableListOf<SharedUserRow>()
        var remaining = userIds.size

        userIds.forEach { uid ->
            db.child("users").child(uid).child("email")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot) {
                        val email = snap.getValue(String::class.java) ?: "(unknown)"
                        rows.add(SharedUserRow(
                            userId = uid,
                            email = email,
                            isOwner = uid == ownerId  // ← Pass owner flag
                        ))
                        if (--remaining == 0) {
                            progress.visibility = View.GONE
                            adapter.submitList(rows.sortedBy { it.email.lowercase() })
                            tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (--remaining == 0) {
                            progress.visibility = View.GONE
                            adapter.submitList(rows)
                            tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                })
        }
    }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT remove user - owner removes target from sharedWith and user_calendars
    //-----------------------------------------------------------------------------------------------
    private fun removeUser(targetUserId: String) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "not signed in", Toast.LENGTH_SHORT).show()
            return
        }

        // ← Safety check - prevent removing owner
        if (targetUserId == ownerId) {
            Toast.makeText(requireContext(), "Cannot remove the owner", Toast.LENGTH_SHORT).show()
            return
        }

        //SUB-SEGMENT multi path update - clear both sides
        //-------------------------------------------------
        val updates = hashMapOf<String, Any?>(
            "calendars/$calendarId/sharedWith/$targetUserId" to null,
            "user_calendars/$targetUserId/$calendarId" to null
        )
        db.updateChildren(updates).addOnCompleteListener { t ->
            if (t.isSuccessful) {
                Toast.makeText(requireContext(), "removed", Toast.LENGTH_SHORT).show()
                loadUsers() // refresh list
            } else {
                Toast.makeText(requireContext(), t.exception?.localizedMessage ?: "remove failed", Toast.LENGTH_LONG).show()
            }
        }
        //-------------------------------------------------
    }
    //-----------------------------------------------------------------------------------------------

    companion object {
        private const val ARG_CAL_ID = "calendarId"

        //SUB-SEGMENT helper - create args bundle
        //-------------------------------------------------
        fun args(calendarId: String) = bundleOf(ARG_CAL_ID to calendarId)
        //-------------------------------------------------
    }
}
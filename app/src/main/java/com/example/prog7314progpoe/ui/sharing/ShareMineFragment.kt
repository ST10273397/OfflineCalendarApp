/**
 * share mine fragment
 * shows the users calendars as cards with share count and share button
 * uses realtime database to read user_calendars and calendars nodes and to perform sharing
 */

package com.example.prog7314progpoe.ui.sharing

import com.example.prog7314progpoe.database.calendar.SharedUserInfo
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import androidx.navigation.fragment.findNavController


class ShareMineFragment : Fragment(R.layout.fragment_share_mine) {

    //SEGMENT firebase - auth and db
    //-----------------------------------------------------------------------------------------------
    private val auth by lazy { FirebaseAuth.getInstance() } // user session
    private val db by lazy { FirebaseDatabase.getInstance().reference } // root
    //-----------------------------------------------------------------------------------------------

    //SEGMENT views and adapter
    //-----------------------------------------------------------------------------------------------
    private lateinit var rv: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView
    private val adapter by lazy {
        ShareMineAdapter(
            onShare = { row ->
                showSharePrompt(row) // open dialog to capture email
            },
            onOpen = { row ->
                //SUB-SEGMENT navigate - open calendar detail with id
                //-------------------------------------------------
                findNavController().navigate(
                    R.id.shareCalendarDetailFragment,
                    ShareCalendarDetailFragment.args(row.id)
                )
                //-------------------------------------------------
            }


        )
    }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT lifecycle - set up list and load data
    //-----------------------------------------------------------------------------------------------
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //SUB-SEGMENT find views - hook layout elements
        //-------------------------------------------------
        rv = view.findViewById(R.id.rvCalendars)
        progress = view.findViewById(R.id.progress)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        //-------------------------------------------------

        //SUB-SEGMENT list setup - layout and adapter
        //-------------------------------------------------
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        //-------------------------------------------------

        //SUB-SEGMENT start load - fetch calendars for current user
        //-------------------------------------------------
        loadMyCalendars()
        //-------------------------------------------------
    }


    override fun onResume() {
        super.onResume()
        loadMyCalendars()
    }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT share prompt - ask for email and perform share
    //-----------------------------------------------------------------------------------------------
    private fun showSharePrompt(row: CalendarRow) {
        val content = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_share_prompt, null, false)
        val inEmail = content.findViewById<EditText>(R.id.inEmail)

        val dlg = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Share calendar")
            .setMessage("enter an email address")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Share", null) // override click
            .create()

        dlg.setOnShowListener {
            val btn = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val email = inEmail.text?.toString()?.trim().orEmpty()
                if (!isEmailValid(email)) {
                    Toast.makeText(requireContext(), "enter a valid email", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                performShare(row.id, email) { ok, msg ->
                    if (ok) dlg.dismiss()
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    if (ok) refreshRow(row.id) // optional refresh of count
                }
            }
        }
        dlg.show()
    }

    //SUB-SEGMENT helper - validate email basic
    //-------------------------------------------------
    private fun isEmailValid(input: String): Boolean {
        if (input.isBlank()) return false
        return !TextUtils.isEmpty(input) && android.util.Patterns.EMAIL_ADDRESS.matcher(input).matches()
    }
    //-------------------------------------------------

    //SUB-SEGMENT perform share - use your existing pattern
    // finds user by email then writes to calendars/<id>/sharedWith and user_calendars/<uid>/<id>
    //-------------------------------------------------
    private fun performShare(calendarId: String, email: String, done: (Boolean, String) -> Unit) {
        val ownerId = auth.currentUser?.uid
        if (ownerId.isNullOrBlank()) {
            done(false, "not signed in")
            return
        }

        // find user by email
        db.child("users").orderByChild("email").equalTo(email.lowercase())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val first = snap.children.firstOrNull()
                    val targetUserId = first?.key
                    if (targetUserId.isNullOrBlank()) {
                        done(false, "no user with that email")
                        return
                    }
                    if (targetUserId == ownerId) {
                        done(false, "cannot share to yourself")
                        return
                    }

                    // ← CHANGED: Create pending invite with SharedUserInfo
                    val inviteInfo = com.example.prog7314progpoe.database.calendar.SharedUserInfo(
                        status = "pending",
                        canEdit = false,
                        canShare = false,
                        invitedAt = System.currentTimeMillis(),
                        acceptedAt = null
                    )

                    // multi path update to link both sides
                    val updates = hashMapOf<String, Any>(
                        "calendars/$calendarId/sharedWith/$targetUserId" to inviteInfo,  // ← FIXED!
                        "user_invites/$targetUserId/$calendarId" to true  // ← NEW: Track pending invites
                    )

                    db.updateChildren(updates).addOnCompleteListener { t ->
                        if (t.isSuccessful) done(true, "invite sent")
                        else done(false, t.exception?.localizedMessage ?: "share failed")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    done(false, error.message)
                }
            })
    }
    //-------------------------------------------------

    //SEGMENT loading - read user_calendars then calendars
    //-----------------------------------------------------------------------------------------------
    private fun loadMyCalendars() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            showEmpty("not signed in")
            return
        }

        progress.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        db.child("user_calendars").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val ids = snap.children.mapNotNull { it.key }
                    if (ids.isEmpty()) {
                        adapter.submitList(emptyList())
                        showEmpty("no calendars yet")
                        return
                    }
                    fetchOwnedCalendars(ids, uid)  // ← CHANGED THIS LINE
                }

                override fun onCancelled(error: DatabaseError) {
                    adapter.submitList(emptyList())
                    showEmpty(error.message)
                }
            })
    }

    //SUB-SEGMENT fetch calendars - hydrate each id to model and map to rows
    //-----------------------------------------------------------------------------------------------
    private fun fetchOwnedCalendars(ids: List<String>, currentUid: String) {
        val rows = mutableListOf<CalendarRow>()
        var remaining = ids.size

        ids.forEach { id ->
            db.child("calendars").child(id)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(calSnap: DataSnapshot) {
                        val m = calSnap.getValue(CalendarModel::class.java)

                        // ONLY show if I'm the owner
                        if (m != null && m.ownerId == currentUid) {
                            val title = m.title ?: ""
                            val desc = m.description ?: ""

                            // Counts acceped and pending users and not the creator
                            val acceptedCount = m.sharedWith?.count {
                                it.key != m.ownerId && it.value.status == "accepted"
                            } ?: 0
                            val pendingCount = m.sharedWith?.count {
                                it.value.status == "pending"
                            } ?: 0

                            rows.add(CalendarRow(
                                id = id,
                                title = title,
                                description = desc,
                                sharedCount = acceptedCount,
                                pendingCount = pendingCount
                            ))
                        }

                        if (--remaining == 0) {
                            progress.visibility = View.GONE
                            if (rows.isEmpty()) {
                                showEmpty("no calendars found")
                            } else {
                                adapter.submitList(rows.sortedBy { it.title.lowercase() })
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

    //SUB-SEGMENT optional - refresh one row after share to update count
    //-----------------------------------------------------------------------------------------------
    private fun refreshRow(calendarId: String) {
        db.child("calendars").child(calendarId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val m = snap.getValue(CalendarModel::class.java) ?: return
                    val current = adapter.currentList.toMutableList()
                    val idx = current.indexOfFirst { it.id == calendarId }
                    if (idx >= 0) {
                        // Count accepted and pending properly - EXCLUDE OWNER
                        val acceptedCount = m.sharedWith?.count {
                            it.key != m.ownerId && it.value.status == "accepted"  // ← Added owner exclusion
                        } ?: 0
                        val pendingCount = m.sharedWith?.count {
                            it.value.status == "pending"
                        } ?: 0

                        current[idx] = current[idx].copy(
                            sharedCount = acceptedCount,
                            pendingCount = pendingCount
                        )
                        adapter.submitList(current.toList())
                    }
                }
                override fun onCancelled(error: DatabaseError) { /* no op */ }
            })
    }
    //-----------------------------------------------------------------------------------------------

    //SUB-SEGMENT helper - show empty message
    //-----------------------------------------------------------------------------------------------
    private fun showEmpty(msg: String) {
        progress.visibility = View.GONE
        tvEmpty.text = msg
        tvEmpty.visibility = View.VISIBLE
    }
    //-----------------------------------------------------------------------------------------------
}

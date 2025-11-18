

package com.example.prog7314progpoe.ui.sharing

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import androidx.navigation.fragment.findNavController

class ShareSharedWithMeFragment : Fragment(R.layout.fragment_share_shared_with_me) {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance().reference }

    private lateinit var rv: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView

    private val adapter by lazy {
        ShareSharedWithMeAdapter(
            onOpen = { row ->
                findNavController().navigate(
                    R.id.shareSharedWithMeDetailFragment,
                    ShareSharedWithMeDetailFragment.args(row.id)
                )
            }
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rv = view.findViewById(R.id.rvCalendars)
        progress = view.findViewById(R.id.progress)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        loadSharedWithMe()
    }

    override fun onResume() {
        super.onResume()
        loadSharedWithMe()
    }

    private fun loadSharedWithMe() {
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
                        showEmpty("no shared calendars")
                        return
                    }
                    fetchSharedCalendars(ids, uid)
                }

                override fun onCancelled(error: DatabaseError) {
                    adapter.submitList(emptyList())
                    showEmpty(error.message)
                }
            })
    }

    private fun fetchSharedCalendars(ids: List<String>, currentUid: String) {
        val rows = mutableListOf<SharedWithMeRow>()
        var remaining = ids.size

        ids.forEach { id ->
            db.child("calendars").child(id)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(calSnap: DataSnapshot) {
                        val calendar = calSnap.getValue(CalendarModel::class.java)

                        if (calendar != null) {
                            val ownerId = calendar.ownerId ?: ""

                            // FILTER: Only show if I'm NOT the owner AND my status is accepted
                            if (ownerId != currentUid) {
                                val myInfo = calendar.sharedWith?.get(currentUid)
                                if (myInfo?.status == "accepted") {
                                    // Fetch owner email
                                    db.child("users").child(ownerId)
                                        .addListenerForSingleValueEvent(object : ValueEventListener {
                                            override fun onDataChange(userSnap: DataSnapshot) {
                                                val ownerEmail = userSnap.child("email").getValue(String::class.java) ?: "Unknown"

                                                rows.add(SharedWithMeRow(
                                                    id = id,
                                                    title = calendar.title ?: "",
                                                    description = calendar.description ?: "",
                                                    ownerEmail = ownerEmail,
                                                    canEdit = myInfo.canEdit == true,
                                                    canShare = myInfo.canShare == true
                                                ))

                                                if (--remaining == 0) {
                                                    progress.visibility = View.GONE
                                                    if (rows.isEmpty()) {
                                                        showEmpty("no shared calendars")
                                                    } else {
                                                        adapter.submitList(rows.sortedBy { it.title.lowercase() })
                                                    }
                                                }
                                            }

                                            override fun onCancelled(error: DatabaseError) {
                                                if (--remaining == 0) {
                                                    progress.visibility = View.GONE
                                                    if (rows.isEmpty()) {
                                                        showEmpty("no shared calendars")
                                                    } else {
                                                        adapter.submitList(rows.sortedBy { it.title.lowercase() })
                                                    }
                                                }
                                            }
                                        })
                                } else {
                                    if (--remaining == 0) {
                                        progress.visibility = View.GONE
                                        if (rows.isEmpty()) {
                                            showEmpty("no shared calendars")
                                        } else {
                                            adapter.submitList(rows.sortedBy { it.title.lowercase() })
                                        }
                                    }
                                }
                            } else {
                                if (--remaining == 0) {
                                    progress.visibility = View.GONE
                                    if (rows.isEmpty()) {
                                        showEmpty("no shared calendars")
                                    } else {
                                        adapter.submitList(rows.sortedBy { it.title.lowercase() })
                                    }
                                }
                            }
                        } else {
                            if (--remaining == 0) {
                                progress.visibility = View.GONE
                                if (rows.isEmpty()) {
                                    showEmpty("no shared calendars")
                                } else {
                                    adapter.submitList(rows.sortedBy { it.title.lowercase() })
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

    private fun showEmpty(msg: String) {
        progress.visibility = View.GONE
        tvEmpty.text = msg
        tvEmpty.visibility = View.VISIBLE
    }
}

data class SharedWithMeRow(
    val id: String,
    val title: String,
    val description: String,
    val ownerEmail: String,
    val canEdit: Boolean,
    val canShare: Boolean
)
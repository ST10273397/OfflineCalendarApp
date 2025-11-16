/**
 * invite detail fragment
 * shows the calendar name and description for a calendar shared to the user
 */

package com.example.prog7314progpoe.ui.sharing

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.google.firebase.database.*

class InviteDetailFragment : Fragment(R.layout.fragment_invite_detail) {

    //SEGMENT args and db
    //-----------------------------------------------------------------------------------------------
    private val calendarId: String by lazy {
        requireArguments().getString(ARG_CAL_ID).orEmpty()
    }
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT views
    //-----------------------------------------------------------------------------------------------
    private lateinit var tvTitle: TextView
    private lateinit var tvDesc: TextView
    //-----------------------------------------------------------------------------------------------

    //SEGMENT lifecycle - load details
    //-----------------------------------------------------------------------------------------------
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvDesc = view.findViewById(R.id.tvDesc)
        loadDetails()
    }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT load - read calendars/<id>
    //-----------------------------------------------------------------------------------------------
    private fun loadDetails() {
        db.child("calendars").child(calendarId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val m = snap.getValue(CalendarModel::class.java)
                    tvTitle.text = m?.title ?: "(Untitled)"
                    tvDesc.text = m?.description.orEmpty()
                }
                override fun onCancelled(error: DatabaseError) { /* no op */ }
            })
    }
    //-----------------------------------------------------------------------------------------------

    companion object {
        private const val ARG_CAL_ID = "calendarId"
        fun args(calendarId: String) = bundleOf(ARG_CAL_ID to calendarId)
    }
}

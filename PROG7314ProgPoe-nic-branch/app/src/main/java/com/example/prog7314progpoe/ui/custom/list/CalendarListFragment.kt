/**
Shows all of the user’s custom calendars in a simple list/grid with titles and brief info.
Tapping a calendar opens its details; a “create” path leads into the management screens.
 */

package com.example.prog7314progpoe.ui.custom.list

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.example.prog7314progpoe.database.calendar.FirebaseCalendarDbHelper
import com.example.prog7314progpoe.ui.custom.shell.CustomCalendarActivity
import com.example.prog7314progpoe.ui.custom.detail.CalendarDetailActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

class CalendarListFragment : Fragment(R.layout.fragment_calendar_list) {

    private lateinit var searchEt: EditText
    private lateinit var emptyTv: TextView
    private lateinit var rv: RecyclerView
    private lateinit var addBtn: MaterialButton

    private val adapter = CalendarListAdapter { cal -> openDetail(cal) }
    private var fullList: List<CalendarModel> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchEt = view.findViewById(R.id.etSearch)
        emptyTv = view.findViewById(R.id.tvEmptyState)
        rv = view.findViewById(R.id.rvCalendars)
        addBtn = view.findViewById(R.id.btnAddCalendar)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        addBtn.setOnClickListener {
            startActivity(Intent(requireContext(), CustomCalendarActivity::class.java))
        }

        searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseCalendarDbHelper.getUserCalendars(uid) { list ->
            fullList = list.sortedBy { it.title?.lowercase() ?: "" }
            adapter.submitList(fullList)
            filter(searchEt.text?.toString().orEmpty())
        }
    }

    private fun filter(q: String) {
        val filtered = if (q.isBlank()) {
            fullList
        } else {
            fullList.filter {
                (it.title ?: "").contains(q, ignoreCase = true) ||
                        (it.description ?: "").contains(q, ignoreCase = true)
            }
        }
        adapter.submitList(filtered)
        emptyTv.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openDetail(cal: CalendarModel) {
        val i = Intent(requireContext(), CalendarDetailActivity::class.java)
        i.putExtra(CalendarDetailActivity.Companion.EXTRA_CAL_ID, cal.calendarId)
        startActivity(i)
    }
}
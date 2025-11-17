/**
Shows one calendar’s upcoming and past events in a scrollable list
From here the user can add a new event or open an existing one to edit
returns to this screen after saves
 */

package com.example.prog7314progpoe.ui.custom.detail

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.example.prog7314progpoe.database.calendar.FirebaseCalendarDbHelper
import com.example.prog7314progpoe.database.holidays.FirebaseHolidayDbHelper
import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.example.prog7314progpoe.ui.custom.events.EventListAdapter
import com.example.prog7314progpoe.ui.custom.events.CreateEventActivity
import com.example.prog7314progpoe.ui.custom.edit.EditCalendarActivity
import com.google.firebase.auth.FirebaseAuth

class CalendarDetailFragment : Fragment(R.layout.fragment_calendar_detail) {

    private lateinit var tvTitle: TextView
    private lateinit var tvDesc: TextView
    private lateinit var btnAdd: Button
    private lateinit var btnEdit: Button
    private lateinit var btnDelete: Button
    private lateinit var rv: RecyclerView
    private lateinit var empty: TextView

    private lateinit var adapter: EventListAdapter

    private var calendarId: String? = null
    private var current: CalendarModel? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTitle = view.findViewById(R.id.tvCalTitle)
        tvDesc  = view.findViewById(R.id.tvCalDesc)
        btnAdd  = view.findViewById(R.id.btnAddEvent)
        btnEdit = view.findViewById(R.id.btnEditCal)
        btnDelete = view.findViewById(R.id.btnDeleteCal)
        rv = view.findViewById(R.id.rvEvents)
        empty = view.findViewById(R.id.tvEmpty) // add a TextView with this id in the layout, or remove if not needed

        // calendarId: prefer Activity extra, fallback to Fragment args if you add them later
        val host = requireActivity() as CalendarDetailActivity
        calendarId = host.intent.getStringExtra(CalendarDetailActivity.EXTRA_CAL_ID)

        adapter = EventListAdapter(
            onOpen = { openEvent(it) },
            onEdit = { editEvent(it) },
            onDelete = { deleteEvent(it) }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        btnAdd.setOnClickListener { addEvent() }
        btnEdit.setOnClickListener { editCal() }
        btnDelete.setOnClickListener { confirmDelete() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val id = calendarId ?: return

        // Load calendar info
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseCalendarDbHelper.getUserCalendars(uid) { list ->
            current = list.firstOrNull { it.calendarId == id }
            tvTitle.text = current?.title ?: "(Untitled)"
            tvDesc.text  = current?.description ?: ""
        }

        // Load events
        FirebaseHolidayDbHelper.getAllHolidays(id) { events ->
            val sorted = events.sortedWith(
                compareBy<HolidayModel> { it.date?.iso ?: "" }
                    .thenBy { it.timeStart ?: Long.MAX_VALUE }
            )
            requireActivity().runOnUiThread {
                adapter.submitList(sorted)
                // if you have a dedicated empty text view in this layout:
                empty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun addEvent() {
        val i = Intent(requireContext(), CreateEventActivity::class.java)
        // Optional: pass calendarId so CreateEventActivity can preselect it later if you add that logic
        i.putExtra("calendarId", calendarId)
        startActivity(i)
    }

    private fun editCal() {
        // Your EditCalendarActivity uses a Spinner to pick which calendar to edit—fine for now
        startActivity(Intent(requireContext(), EditCalendarActivity::class.java))
    }

    private fun openEvent(ev: HolidayModel) {
        // Hook up to your EventDetailActivity when ready
        Toast.makeText(requireContext(), "Open: ${ev.name ?: ev.holidayId}", Toast.LENGTH_SHORT).show()
    }

    private fun editEvent(ev: HolidayModel) {
        // Hook up to your EditEventActivity when ready
        Toast.makeText(requireContext(), "Edit: ${ev.name ?: ev.holidayId}", Toast.LENGTH_SHORT).show()
    }

    private fun deleteEvent(ev: HolidayModel) {
        val calId = calendarId ?: return
        val eventId = ev.holidayId ?: return
        AlertDialog.Builder(requireContext())
            .setTitle("Delete event")
            .setMessage("Delete this event?")
            .setPositiveButton("Delete") { d, _ ->
                FirebaseHolidayDbHelper.deleteHoliday(calId, eventId) {
                    Toast.makeText(requireContext(), "Event deleted", Toast.LENGTH_SHORT).show()
                    load() // refresh list
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete() {
        val id = calendarId ?: return
        val ownerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        AlertDialog.Builder(requireContext())
            .setTitle("Delete calendar")
            .setMessage("This will delete the calendar for all who have access. Continue?")
            .setPositiveButton("Delete") { d, _ ->
                FirebaseCalendarDbHelper.deleteCalendar(id, ownerId) {
                    requireActivity().finish()
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
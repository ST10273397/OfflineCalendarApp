/**
Draws each event row in the calendar details list: title, date, and any quick status.
Row taps can open the event for editing.
 */
package com.example.prog7314progpoe.ui.custom.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.holidays.HolidayModel

class EventAdapter(
    private var items: List<HolidayModel>,
    private val onClick: (HolidayModel) -> Unit
) : RecyclerView.Adapter<EventAdapter.VH>() {

    fun submit(list: List<HolidayModel>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.tvEventTitle)
        private val date: TextView = v.findViewById(R.id.tvEventDate)
        fun bind(item: HolidayModel) {
            title.text = item.name ?: "(Untitled event)"
            date.text = item.date?.iso ?: ""
            itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_event_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size
}
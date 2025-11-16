/**
 * Presents events in a larger “card” style when you want a more visual list
 * Each card shows the key fields and supports tapping through to edit
 */

package com.example.prog7314progpoe.ui.custom.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.google.android.material.button.MaterialButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EventListAdapter(
    private val onOpen: (HolidayModel) -> Unit,
    private val onEdit: (HolidayModel) -> Unit,
    private val onDelete: (HolidayModel) -> Unit
) : ListAdapter<HolidayModel, EventListAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_event_card, parent, false)
        return VH(v, onOpen, onEdit, onDelete)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(
        itemView: View,
        private val onOpen: (HolidayModel) -> Unit,
        private val onEditClick: (HolidayModel) -> Unit,
        private val onDeleteClick: (HolidayModel) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvWhen: TextView = itemView.findViewById(R.id.tvWhen)
        private val tvDesc: TextView = itemView.findViewById(R.id.tvDesc)
        private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)

        private val zone = ZoneId.systemDefault()
        private val tFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

        fun bind(m: HolidayModel) {
            // title/name fallback
            val title = m.name ?: runCatching {
                val f = m.javaClass.getDeclaredField("title"); f.isAccessible = true
                (f.get(m) as? String)
            }.getOrNull() ?: "(Untitled)"
            tvTitle.text = title

            // date/time line
            val dateStr = m.date?.iso ?: m.date?.toString() ?: ""
            val start = m.timeStart ?: 0L
            val end = m.timeEnd ?: 0L
            val whenLine = buildString {
                if (dateStr.length >= 10) append(dateStr.substring(0, 10)).append("  ")
                if (start > 0L) append(tFmt.format(Instant.ofEpochMilli(start)))
                if (end > 0L && end > start) append(" – ").append(tFmt.format(Instant.ofEpochMilli(end)))
            }.ifBlank { "—" }
            tvWhen.text = whenLine

            tvDesc.text = m.desc.orEmpty()

            itemView.setOnClickListener { onOpen(m) }
            btnDelete.setOnClickListener { onDeleteClick(m) }
        }
    }

    companion object {//help
        private val DIFF = object : DiffUtil.ItemCallback<HolidayModel>() {
            override fun areItemsTheSame(o: HolidayModel, n: HolidayModel) = o.holidayId == n.holidayId
            override fun areContentsTheSame(o: HolidayModel, n: HolidayModel) = o == n
        }
    }
}
package com.example.prog7314progpoe.ui.meetings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.meeting.MeetingModel
import java.text.SimpleDateFormat
import java.util.*

class MeetingsAdapter(
    private val onClick: (MeetingModel) -> Unit
) : ListAdapter<MeetingModel, MeetingsAdapter.MeetingViewHolder>(MeetingDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeetingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meeting_card, parent, false)
        return MeetingViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: MeetingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MeetingViewHolder(
        itemView: View,
        private val onClick: (MeetingModel) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val titleText: TextView = itemView.findViewById(R.id.txtMeetingTitle)
        private val dateText: TextView = itemView.findViewById(R.id.txtMeetingDate)
        private val timeText: TextView = itemView.findViewById(R.id.txtMeetingTime)
        private val participantsText: TextView = itemView.findViewById(R.id.txtParticipants)

        fun bind(meeting: MeetingModel) {
            titleText.text = meeting.title ?: "Untitled Meeting"

            //ormat date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            dateText.text = meeting.date?.iso ?: "No date"

             //Format time
            if (meeting.timeStart != null && meeting.timeEnd != null) {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val start = timeFormat.format(Date(meeting.timeStart!!))
                val end = timeFormat.format(Date(meeting.timeEnd!!))
                timeText.text = "$start - $end"
            } else {
                timeText.text = "No time set"
            }

            // Show participant count
            val count = meeting.participants?.size ?: 0
            participantsText.text = "$count participant${if (count != 1) "s" else ""}"

            itemView.setOnClickListener {
                onClick(meeting)
            }
        }
    }

    private class MeetingDiffCallback : DiffUtil.ItemCallback<MeetingModel>() {
        override fun areItemsTheSame(oldItem: MeetingModel, newItem: MeetingModel): Boolean {
            return oldItem.meetingId == newItem.meetingId
        }

        override fun areContentsTheSame(oldItem: MeetingModel, newItem: MeetingModel): Boolean {
            return oldItem == newItem
        }
    }
}
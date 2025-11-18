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
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

// displays meeting invite cards with accept/decline buttons
class MeetingInvitesAdapter(
    private val onAccept: (MeetingModel) -> Unit,
    private val onDecline: (MeetingModel) -> Unit
) : ListAdapter<MeetingModel, MeetingInvitesAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meeting_invite_card, parent, false)
        return VH(v, onAccept, onDecline)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        itemView: View,
        private val onAccept: (MeetingModel) -> Unit,
        private val onDecline: (MeetingModel) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvTitle = itemView.findViewById<TextView>(R.id.tvTitle)
        private val tvCreator = itemView.findViewById<TextView>(R.id.tvCreator)
        private val tvDateTime = itemView.findViewById<TextView>(R.id.tvDateTime)
        private val btnAccept = itemView.findViewById<MaterialButton>(R.id.btnAccept)
        private val btnDecline = itemView.findViewById<MaterialButton>(R.id.btnDecline)

        fun bind(meeting: MeetingModel) {
            tvTitle.text = meeting.title ?: "Untitled Meeting"

            // show who invited you
            val creatorEmail = meeting.participants?.get(meeting.creatorId)?.email ?: "Unknown"
            tvCreator.text = "Invited by $creatorEmail"

            // format date and time
            val dateStr = meeting.date?.iso ?: ""
            val timeStr = if (meeting.timeStart != null) {
                val format = SimpleDateFormat("HH:mm", Locale.getDefault())
                format.format(Date(meeting.timeStart!!))
            } else {
                ""
            }
            tvDateTime.text = "$dateStr at $timeStr"

            btnAccept.setOnClickListener { onAccept(meeting) }
            btnDecline.setOnClickListener { onDecline(meeting) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MeetingModel>() {
            override fun areItemsTheSame(o: MeetingModel, n: MeetingModel) =
                o.meetingId == n.meetingId
            override fun areContentsTheSame(o: MeetingModel, n: MeetingModel) = o == n
        }
    }
}
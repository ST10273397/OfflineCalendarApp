package com.example.prog7314progpoe.ui.meetings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.prog7314progpoe.R

// shows list of meeting participants with their response status
class ParticipantsAdapter : ListAdapter<ParticipantRow, ParticipantsAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_participant_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEmail = itemView.findViewById<TextView>(R.id.tvEmail)
        private val tvStatus = itemView.findViewById<TextView>(R.id.tvStatus)

        fun bind(participant: ParticipantRow) {
            tvEmail.text = if (participant.isCreator) {
                "${participant.email} (Creator)"
            } else {
                participant.email
            }

            // show status with appropriate formatting
            val statusText = when (participant.status) {
                "accepted" -> "✓ Accepted"
                "declined" -> "✗ Declined"
                "pending" -> "⋯ Pending"
                else -> participant.status
            }
            tvStatus.text = statusText
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ParticipantRow>() {
            override fun areItemsTheSame(o: ParticipantRow, n: ParticipantRow) =
                o.email == n.email
            override fun areContentsTheSame(o: ParticipantRow, n: ParticipantRow) = o == n
        }
    }
}

data class ParticipantRow(
    val email: String,
    val status: String,
    val isCreator: Boolean
)
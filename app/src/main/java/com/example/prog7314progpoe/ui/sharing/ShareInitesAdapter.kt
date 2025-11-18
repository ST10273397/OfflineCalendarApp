

package com.example.prog7314progpoe.ui.sharing

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.prog7314progpoe.R
import com.google.android.material.button.MaterialButton

class ShareInvitesAdapter(
    private val onAccept: (InviteRow) -> Unit,
    private val onDecline: (InviteRow) -> Unit
) : ListAdapter<InviteRow, ShareInvitesAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invite_card, parent, false)
        return VH(v, onAccept, onDecline)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        itemView: View,
        private val onAccept: (InviteRow) -> Unit,
        private val onDecline: (InviteRow) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvTitle = itemView.findViewById<TextView>(R.id.tvTitle)
        private val tvOwner = itemView.findViewById<TextView>(R.id.tvOwner)
        private val btnAccept = itemView.findViewById<MaterialButton>(R.id.btnAccept)
        private val btnDecline = itemView.findViewById<MaterialButton>(R.id.btnDecline)

        fun bind(invite: InviteRow) {
            tvTitle.text = invite.title.ifBlank { "(Untitled)" }
            tvOwner.text = "Invited by ${invite.ownerEmail}"

            btnAccept.setOnClickListener { onAccept(invite) }
            btnDecline.setOnClickListener { onDecline(invite) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<InviteRow>() {
            override fun areItemsTheSame(o: InviteRow, n: InviteRow) = o.calendarId == n.calendarId
            override fun areContentsTheSame(o: InviteRow, n: InviteRow) = o == n
        }
    }
}
/**
 * invite list adapter
 * shows calendars that are shared to the current user with a leave action
 */

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

class InviteListAdapter(
    private val onOpen: (SharedToMeRow) -> Unit,
    private val onLeave: (SharedToMeRow) -> Unit
) : ListAdapter<SharedToMeRow, InviteListAdapter.VH>(DIFF) {

    //SEGMENT create - inflate item
    //-----------------------------------------------------------------------------------------------
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invite_card, parent, false)
        return VH(v, onOpen, onLeave)
    }

    //SEGMENT bind - attach data
    //-----------------------------------------------------------------------------------------------
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    //SEGMENT view holder
    //-----------------------------------------------------------------------------------------------
    class VH(
        itemView: View,
        private val onOpen: (SharedToMeRow) -> Unit,
        private val onLeave: (SharedToMeRow) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvTitle = itemView.findViewById<TextView>(R.id.tvTitle)
        private val tvOwner = itemView.findViewById<TextView>(R.id.tvOwner)
        private val btnLeave = itemView.findViewById<MaterialButton>(R.id.btnLeave)

        fun bind(row: SharedToMeRow) {
            tvTitle.text = row.title.ifBlank { "(Untitled)" }
            tvOwner.text = row.ownerEmail
            itemView.setOnClickListener { onOpen(row) }
            btnLeave.setOnClickListener { onLeave(row) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SharedToMeRow>() {
            override fun areItemsTheSame(o: SharedToMeRow, n: SharedToMeRow) = o.calendarId == n.calendarId
            override fun areContentsTheSame(o: SharedToMeRow, n: SharedToMeRow) = o == n
        }
    }
}

//SEGMENT ui row model for invites list
//-----------------------------------------------------------------------------------------------
data class SharedToMeRow(
    val calendarId: String,
    val title: String,
    val ownerEmail: String
)

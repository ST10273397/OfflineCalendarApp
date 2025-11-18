/**
 * Share shared with me adapter
 * Displays calendars shared with the current user
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

class ShareSharedWithMeAdapter(
    private val onOpen: (SharedWithMeRow) -> Unit
) : ListAdapter<SharedWithMeRow, ShareSharedWithMeAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shared_with_me_card, parent, false)
        return VH(v, onOpen)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        itemView: View,
        private val onOpen: (SharedWithMeRow) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvTitle = itemView.findViewById<TextView>(R.id.tvTitle)
        private val tvOwner = itemView.findViewById<TextView>(R.id.tvOwner)
        private val tvPermissions = itemView.findViewById<TextView>(R.id.tvPermissions)

        fun bind(row: SharedWithMeRow) {
            tvTitle.text = row.title.ifBlank { "(Untitled)" }
            tvOwner.text = "Shared by ${row.ownerEmail}"

            // Show permissions
            val permissions = mutableListOf<String>()
            if (row.canEdit) permissions.add("Can Edit")
            if (row.canShare) permissions.add("Can Share")

            tvPermissions.text = if (permissions.isEmpty()) {
                "View only"
            } else {
                permissions.joinToString(", ")
            }

            itemView.setOnClickListener { onOpen(row) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SharedWithMeRow>() {
            override fun areItemsTheSame(o: SharedWithMeRow, n: SharedWithMeRow) = o.id == n.id
            override fun areContentsTheSame(o: SharedWithMeRow, n: SharedWithMeRow) = o == n
        }
    }
}
/**
 * share mine adapter
 * binds each calendar into a card with name description share count and share button
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

class ShareMineAdapter(
    private val onShare: (CalendarRow) -> Unit,
    private val onOpen: (CalendarRow) -> Unit
) : ListAdapter<CalendarRow, ShareMineAdapter.VH>(DIFF) {

    //SEGMENT create - inflate item view holder
    //-----------------------------------------------------------------------------------------------
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_share_calendar_card, parent, false)
        return VH(v, onShare, onOpen)
    }

    //SEGMENT bind - attach data to views
    //-----------------------------------------------------------------------------------------------
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    //SEGMENT view holder - item bindings
    //-----------------------------------------------------------------------------------------------
    class VH(
        itemView: View,
        private val onShare: (CalendarRow) -> Unit,
        private val onOpen: (CalendarRow) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        //SUB-SEGMENT find views
        //-------------------------------------------------
        private val tvTitle = itemView.findViewById<TextView>(R.id.tvTitle)
        private val tvDesc = itemView.findViewById<TextView>(R.id.tvDesc)
        private val tvShareCount = itemView.findViewById<TextView>(R.id.tvShareCount)
        private val btnShare = itemView.findViewById<MaterialButton>(R.id.btnShare)
        //-------------------------------------------------

        //SUB-SEGMENT bind values and clicks
        //-------------------------------------------------
        fun bind(row: CalendarRow) {
            tvTitle.text = row.title.ifBlank { "(Untitled)" }
            tvDesc.text = row.description

            // Show both accepted and pending counts
            val countText = if (row.pendingCount > 0) {
                "${row.sharedCount} users (${row.pendingCount} pending)"
            } else {
                "${row.sharedCount} users"
            }
            tvShareCount.text = countText

            itemView.setOnClickListener { onOpen(row) }
            btnShare.setOnClickListener { onShare(row) }
        }
        //-------------------------------------------------
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CalendarRow>() {
            override fun areItemsTheSame(o: CalendarRow, n: CalendarRow) = o.id == n.id
            override fun areContentsTheSame(o: CalendarRow, n: CalendarRow) = o == n
        }
    }
}

//SEGMENT ui row model for the list
//-----------------------------------------------------------------------------------------------
data class CalendarRow(
    val id: String,
    val title: String,
    val description: String,
    val sharedCount: Int,
    val pendingCount: Int = 0
)

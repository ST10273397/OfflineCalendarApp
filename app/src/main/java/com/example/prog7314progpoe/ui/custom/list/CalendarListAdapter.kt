/**
Renders each calendar card: name, short description, and tap area.
It wires item taps to open the detail screen for that calendar.
 */

package com.example.prog7314progpoe.ui.custom.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.CalendarModel

class CalendarListAdapter(
    private val onClick: (CalendarModel) -> Unit
) : RecyclerView.Adapter<CalendarListAdapter.VH>() {

    private val items = mutableListOf<CalendarModel>()

    fun submitList(list: List<CalendarModel>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: CardView = itemView.findViewById(R.id.cardRoot)
        val title: TextView = itemView.findViewById(R.id.tvTitle)
        val desc: TextView = itemView.findViewById(R.id.tvDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title.orEmpty()
        holder.desc.text = item.description.orEmpty()
        holder.card.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
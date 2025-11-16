/**
 * shared users adapter
 * shows a list of people who have access to the calendar with a remove button
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

class SharedUsersAdapter(
    private val onRemove: (SharedUserRow) -> Unit
) : ListAdapter<SharedUserRow, SharedUsersAdapter.VH>(DIFF) {

    //SEGMENT create - inflate row
    //-----------------------------------------------------------------------------------------------
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shared_user_row, parent, false)
        return VH(v, onRemove)
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
        private val onRemove: (SharedUserRow) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvEmail = itemView.findViewById<TextView>(R.id.tvEmail)
        private val btnRemove = itemView.findViewById<MaterialButton>(R.id.btnRemove)

        fun bind(row: SharedUserRow) {
            tvEmail.text = row.email
            btnRemove.setOnClickListener { onRemove(row) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SharedUserRow>() {
            override fun areItemsTheSame(o: SharedUserRow, n: SharedUserRow) = o.userId == n.userId
            override fun areContentsTheSame(o: SharedUserRow, n: SharedUserRow) = o == n
        }
    }
}

//SEGMENT ui row model for shared users
//-----------------------------------------------------------------------------------------------
data class SharedUserRow(
    val userId: String,
    val email: String
)

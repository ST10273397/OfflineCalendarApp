

package com.example.prog7314progpoe.ui.sharing

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.database.calendar.CalendarModel
import com.example.prog7314progpoe.database.calendar.FirebaseCalendarDbHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ShareSharedWithMeDetailFragment : Fragment(R.layout.fragment_share_shared_with_me_detail) {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance().reference }

    private lateinit var tvTitle: TextView
    private lateinit var tvOwnerEmail: TextView
    private lateinit var tvYourPermissions: TextView
    private lateinit var btnLeave: Button
    private lateinit var progress: ProgressBar

    private var calendarId: String = ""

    companion object {
        fun args(calendarId: String) = bundleOf("calendarId" to calendarId)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarId = arguments?.getString("calendarId") ?: ""

        tvTitle = view.findViewById(R.id.tvTitle)
        tvOwnerEmail = view.findViewById(R.id.tvOwnerEmail)
        tvYourPermissions = view.findViewById(R.id.tvYourPermissions)
        btnLeave = view.findViewById(R.id.btnLeave)
        progress = view.findViewById(R.id.progress)

        btnLeave.setOnClickListener { leaveCalendar() }

        loadDetails()
    }

    private fun loadDetails() {
        val uid = auth.currentUser?.uid ?: return

        progress.visibility = View.VISIBLE

        db.child("calendars").child(calendarId).get()
            .addOnSuccessListener { snapshot ->
                val calendar = snapshot.getValue(CalendarModel::class.java)
                if (calendar == null) {
                    Toast.makeText(requireContext(), "Calendar not found", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                    return@addOnSuccessListener
                }

                tvTitle.text = calendar.title ?: "Untitled"

                // Get owner email
                val ownerId = calendar.ownerId ?: ""
                db.child("users").child(ownerId).get()
                    .addOnSuccessListener { userSnap ->
                        val ownerEmail = userSnap.child("email").getValue(String::class.java) ?: "Unknown"
                        tvOwnerEmail.text = "Owner: $ownerEmail"
                    }

                // Get your permissions
                val myInfo = calendar.sharedWith?.get(uid)
                val permissions = mutableListOf<String>()

                if (myInfo?.canEdit == true) permissions.add("Can Edit")
                if (myInfo?.canShare == true) permissions.add("Can Share")

                tvYourPermissions.text = if (permissions.isEmpty()) {
                    "Your permissions: View only"
                } else {
                    "Your permissions: ${permissions.joinToString(", ")}"
                }

                progress.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                progress.visibility = View.GONE
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun leaveCalendar() {
        progress.visibility = View.VISIBLE

        FirebaseCalendarDbHelper.leaveCalendar(
            calendarId = calendarId,
            onSuccess = {
                Toast.makeText(requireContext(), "Left calendar", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            },
            onError = { msg ->
                progress.visibility = View.GONE
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        )
    }
}
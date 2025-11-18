package com.example.prog7314progpoe.ui.meetings

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class MeetingPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> MyMeetingsFragment()
            1 -> MeetingInvitesFragment()
            else -> MyMeetingsFragment()
        }
    }
}
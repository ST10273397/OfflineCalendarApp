/**
 * sharing pager adapter
 * supplies fragments for mine and invites tabs
 * this does not load data only returns fragment instances
 */

package com.example.prog7314progpoe.ui.sharing

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class SharingPagerAdapter(parent: Fragment) : FragmentStateAdapter(parent) {
    override fun getItemCount(): Int = 3 // Changed from 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ShareMineFragment()           // Calendars I own
            1 -> ShareSharedWithMeFragment()// Pending invites
            2 -> ShareInvitesFragment()        // Pending invites
            else -> ShareMineFragment()
        }
    }
}
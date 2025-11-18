package com.example.prog7314progpoe.ui.meetings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.prog7314progpoe.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MeetingsHostFragment : Fragment(R.layout.fragment_meetings_host) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pager = view.findViewById<ViewPager2>(R.id.pager)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)

        val adapter = MeetingPagerAdapter(this)
        pager.adapter = adapter

        TabLayoutMediator(tabLayout, pager) { tab, position ->
            tab.text = when (position) {
                0 -> "My Meetings"
                1 -> "Invites"
                else -> ""
            }
        }.attach()
    }
}
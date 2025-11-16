/**
 * sharing host fragment
 * shows two tabs mine and invites
 * this only hosts tabs and pager not business logic
 */

package com.example.prog7314progpoe.ui.sharing

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.prog7314progpoe.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class SharingHostFragment : Fragment(R.layout.fragment_sharing_host) {

    //SEGMENT lifecycle - set up tabs and pager
    //-----------------------------------------------------------------------------------------------
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)
        val pager = view.findViewById<ViewPager2>(R.id.pager)

        pager.adapter = SharingPagerAdapter(this)

        TabLayoutMediator(tabLayout, pager) { tab, position ->
            tab.text = when (position) {
                0 -> "Mine"
                1 -> "Shared with Me"  // NEW
                2 -> "Invites"
                else -> "Tab"
            }
        }.attach()
    }
    //-----------------------------------------------------------------------------------------------
}

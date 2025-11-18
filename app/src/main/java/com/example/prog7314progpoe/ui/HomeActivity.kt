/**
 * The Base UI
 * Holds the Navigation Bar
 */

package com.example.prog7314progpoe.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.prog7314progpoe.R
import com.example.prog7314progpoe.offline.OfflineManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView

class HomeActivity : AppCompatActivity() {

    private lateinit var offlineManager: OfflineManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        offlineManager = OfflineManager(this)

        val navController = findNavController(R.id.nav_host_fragment)
        findViewById<BottomNavigationView>(R.id.bottom_nav)
            .setupWithNavController(navController)

        // Show offline banner if not connected
        val offlineBanner = findViewById<MaterialCardView>(R.id.offlineBanner)
        if (!offlineManager.isOnline()) {
            offlineBanner.visibility = View.VISIBLE
        }

    }
}
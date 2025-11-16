/**
Hosts the “Calendar details” screen
It’s the empty frame that holds the detail fragment and shows the selected calendar title and events
 */

package com.example.prog7314progpoe.ui.custom.detail

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.prog7314progpoe.R
import com.google.android.material.appbar.MaterialToolbar

class CalendarDetailActivity : AppCompatActivity(R.layout.activity_calendar_detail_host) {
    companion object {
        const val EXTRA_CAL_ID = "cal_id"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView already provided by constructor param
        val tb = findViewById<MaterialToolbar>(R.id.toolbar)
        tb.setNavigationOnClickListener { finish() }
    }

}
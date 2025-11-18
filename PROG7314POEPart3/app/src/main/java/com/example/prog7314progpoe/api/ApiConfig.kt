package com.example.prog7314progpoe.api

import com.example.prog7314progpoe.BuildConfig

/**
 * Central source for the Calendarific key.
 * Reads from BuildConfig (set in app/build.gradle), with an optional dev fallback.
 */
object ApiConfig {
    fun apiKey(): String {
        val k = BuildConfig.CALENDARIFIC_KEY_1
        return if (!k.isNullOrEmpty()) k else "46QYdu6jxG28Ps2CjXWfcpd4GTiJX9q5" // optional fallback
    }
}

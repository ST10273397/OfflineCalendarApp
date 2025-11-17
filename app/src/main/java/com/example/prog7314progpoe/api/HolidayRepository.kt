package com.example.prog7314progpoe.api

import android.content.Context
import com.google.gson.Gson
import com.example.prog7314progpoe.database.holidays.HolidayResponse  // <-- use your real model package
// If HolidayCache is in a different package, import it explicitly:
// import com.example.prog7314progpoe.api.HolidayCache  // adjust if needed

/**
 * Cache-first access to Calendarific public holidays.
 */
class HolidayRepository(private val context: Context) {

    private val gson = Gson()

    suspend fun getHolidays(countryIso: String, year: Int): HolidayResponse {
        // 1) try cache
        HolidayCache.get(context, countryIso, year)?.let { cached ->
            return gson.fromJson(cached, HolidayResponse::class.java)
        }

        // 2) network
        val resp = ApiClient.api.getHolidays(ApiConfig.apiKey(), countryIso, year)

        // 3) cache snapshot
        HolidayCache.put(context, countryIso, year, gson.toJson(resp))

        return resp
    }
}

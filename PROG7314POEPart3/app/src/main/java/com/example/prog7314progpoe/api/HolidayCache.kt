package com.example.prog7314progpoe.api

//SEGMENT imports - tools
//-----------------------------------------------------------------------------------------------
import android.content.Context // prefs
import com.google.gson.Gson // json
//-----------------------------------------------------------------------------------------------

object HolidayCache {

    //SEGMENT constants - storage and ttl
    //-----------------------------------------------------------------------------------------------
    private const val PREFS = "holiday_cache_v1" // pref file
    private const val KEY_PREFIX = "holidays_" // base key
    private const val TTL_MS = 90L * 24 * 60 * 60 * 1000 // 90 days
    private val gson = Gson() // json
    //-----------------------------------------------------------------------------------------------

    //SEGMENT types - what we store
    //-----------------------------------------------------------------------------------------------
    data class Entry( //Start
        val savedAt: Long, // when cached
        val json: String // raw json of response
    )
    //-----------------------------------------------------------------------------------------------

    //SEGMENT keying - country and year
    //-----------------------------------------------------------------------------------------------
    private fun makeKey(country: String, year: Int): String {
        return KEY_PREFIX + country.uppercase() + "_" + year // simple key
    }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT get - return json if fresh
    //-----------------------------------------------------------------------------------------------
    fun get(context: Context, country: String, year: Int): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) // prefs
        val blob = prefs.getString(makeKey(country, year), null) ?: return null // nothing
        val entry = try { gson.fromJson(blob, Entry::class.java) } catch (_: Exception) { return null } // parse
        val fresh = (System.currentTimeMillis() - entry.savedAt) <= TTL_MS // fresh
        return if (fresh) entry.json else null // ok or null
    }
    //-----------------------------------------------------------------------------------------------

    //SEGMENT put - save json snapshot
    //-----------------------------------------------------------------------------------------------
    fun put(context: Context, country: String, year: Int, json: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) // prefs
        val entry = Entry(savedAt = System.currentTimeMillis(), json = json) // entry
        prefs.edit().putString(makeKey(country, year), gson.toJson(entry)).apply() // write
    }
    //-----------------------------------------------------------------------------------------------
}

package com.example.prog7314progpoe.database

import androidx.room.TypeConverter
import com.example.prog7314progpoe.database.calendar.SharedUserInfo
import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room TypeConverters for converting complex data types into storable formats.
 * Converts objects like maps, lists, and embedded data classes to JSON strings and back.
 */
class Converters {

    private val gson = Gson()

    // -------------------------------
    // SharedUserInfo Map Converters
    // -------------------------------

    /**
     * Converts Map<String, SharedUserInfo> to JSON string for Room storage.
     */
    @TypeConverter
    fun fromSharedUserInfoMap(value: Map<String, SharedUserInfo>?): String? {
        return gson.toJson(value)
    }

    /**
     * Converts JSON string back to Map<String, SharedUserInfo>.
     */
    @TypeConverter
    fun toSharedUserInfoMap(value: String?): Map<String, SharedUserInfo>? {
        if (value.isNullOrEmpty()) return null
        val type = object : TypeToken<Map<String, SharedUserInfo>>() {}.type
        return gson.fromJson(value, type)
    }

    // -------------------------------
    // HolidayModel Map Converters
    // -------------------------------

    /**
     * Converts Map<String, HolidayModel> to JSON string for Room storage.
     */
    @TypeConverter
    fun fromHolidayMap(value: Map<String, HolidayModel>?): String? {
        return gson.toJson(value)
    }

    /**
     * Converts JSON string back to Map<String, HolidayModel>.
     */
    @TypeConverter
    fun toHolidayMap(value: String?): Map<String, HolidayModel>? {
        if (value.isNullOrEmpty()) return null
        val type = object : TypeToken<Map<String, HolidayModel>>() {}.type
        return gson.fromJson(value, type)
    }

    // -------------------------------
    // HolidayModel.DateInfo Converters
    // -------------------------------

    /**
     * Converts a HolidayModel.DateInfo object to JSON string.
     */
    @TypeConverter
    fun fromDateInfo(value: HolidayModel.DateInfo?): String? {
        return gson.toJson(value)
    }

    /**
     * Converts JSON string back to HolidayModel.DateInfo object.
     */
    @TypeConverter
    fun toDateInfo(value: String?): HolidayModel.DateInfo? {
        if (value.isNullOrEmpty()) return null
        return gson.fromJson(value, HolidayModel.DateInfo::class.java)
    }

    // -------------------------------
    // List<String> Converters
    // -------------------------------

    /**
     * Converts a list of strings to JSON string for Room storage.
     */
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return gson.toJson(value)
    }

    /**
     * Converts JSON string back to a List<String>.
     */
    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value.isNullOrEmpty()) return null
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }
}

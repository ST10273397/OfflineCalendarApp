package com.example.prog7314progpoe.database

import androidx.room.TypeConverter
import com.example.prog7314progpoe.database.calendar.SharedUserInfo
import com.example.prog7314progpoe.database.holidays.HolidayModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    // Convert Map<String, SharedUserInfo> to JSON string
    @TypeConverter
    fun fromSharedUserInfoMap(value: Map<String, SharedUserInfo>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toSharedUserInfoMap(value: String?): Map<String, SharedUserInfo>? {
        if (value == null) return null
        val type = object : TypeToken<Map<String, SharedUserInfo>>() {}.type
        return gson.fromJson(value, type)
    }

    // Convert Map<String, HolidayModel> to JSON string
    @TypeConverter
    fun fromHolidayMap(value: Map<String, HolidayModel>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toHolidayMap(value: String?): Map<String, HolidayModel>? {
        if (value == null) return null
        val type = object : TypeToken<Map<String, HolidayModel>>() {}.type
        return gson.fromJson(value, type)
    }

    // Convert DateInfo
    @TypeConverter
    fun fromDateInfo(value: HolidayModel.DateInfo?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toDateInfo(value: String?): HolidayModel.DateInfo? {
        if (value == null) return null
        return gson.fromJson(value, HolidayModel.DateInfo::class.java)
    }

    // Convert List<String>
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }
}
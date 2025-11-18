package com.example.prog7314progpoe.database.holidays

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Wrapper for holiday API response.
 * Maps the response to a Room entity for potential offline storage.
 */
@Entity(tableName = "CalendarHolidays")
data class HolidayResponse(
    var response: HolidayList? = null
) {
    constructor() : this(null)
}

/**
 * Represents a list of holidays, typically returned from an API or stored in database.
 */
@Entity(tableName = "HolidayLists")
data class HolidayList(
    var holidays: List<HolidayModel>? = null
) {
    constructor() : this(emptyList())
}

/**
 * Represents a single holiday entry.
 * Stores all relevant holiday information, including optional date ranges, repeat patterns, and type.
 */
@Entity(tableName = "Holidays")
data class HolidayModel(
    @PrimaryKey
    var holidayId: String,           // Unique ID, mapping to Firebase key
    var name: String? = null,        // Holiday name
    var desc: String? = null,        // Optional description
    @Embedded
    var date: DateInfo? = null,      // Single-day holiday date
    var dateStart: DateInfo? = null, // Start date for multi-day holidays
    var dateEnd: DateInfo? = null,   // End date for multi-day holidays
    var timeStart: Long? = null,     // Start timestamp (optional)
    var timeEnd: Long? = null,       // End timestamp (optional)
    var repeat: List<String>? = null,// Repeat rules, e.g., ["Daily", "Weekly", "Annually"]
    var type: List<String>? = null,  // Holiday types, e.g., ["National", "Religious"]
    val country: String? = null,     // Country ISO code
    val sourceId: String? = null,    // Calendar ID for custom, or country ISO for public
    val sourceType: String? = null,  // "custom" or "public"
    val cachedAt: Long? = null       // Timestamp for caching purposes
) {
    constructor() : this(
        "", null, null, null, null, null, null, null, null, null, null, null, null, null
    )

    /**
     * Represents a holiday date in ISO format.
     */
    data class DateInfo(
        var iso: String? = null // e.g., "2025-01-01"
    ) {
        constructor() : this(null)
    }
}

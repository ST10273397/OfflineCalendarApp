package com.example.prog7314progpoe.database.holidays

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "CalendarHolidays")
data class HolidayResponse(
    var response: HolidayList? = null
) {
    constructor() : this(null)
}

@Entity(tableName = "HolidayLists")
data class HolidayList(
    var holidays: List<HolidayModel>? = null
) {
    constructor() : this(emptyList())
}

@Entity(tableName = "Holidays")
data class HolidayModel(
    @PrimaryKey val holidayId: String? = null, // Mapping to Firebase key
    var name: String? = null,
    var desc: String? = null,
    var date: DateInfo? = null,
    var dateStart: DateInfo? = null,  // Optional if holiday spans multiple days
    var dateEnd: DateInfo? = null,
    var timeStart: Long? = null,
    var timeEnd: Long? = null,
    var repeat: List<String>? = null, // Example: ["Daily", "Weekly", "Monthly", "Annually"]
    var type: List<String>? = null    // Example: ["National holiday", "Religious"]
) {
    constructor() : this(
        null, null, null, null, null, null, null, null, null, null
    )

    data class DateInfo(
        var iso: String? = null // e.g., "2025-01-01"
    ) {
        constructor() : this(null)
    }
}

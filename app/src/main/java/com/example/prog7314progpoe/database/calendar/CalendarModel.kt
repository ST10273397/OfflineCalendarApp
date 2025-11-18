package com.example.prog7314progpoe.database.calendar

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.prog7314progpoe.database.holidays.HolidayModel

@Entity(tableName = "Calendars")
data class CalendarModel(
    @PrimaryKey var calendarId: String,
    var title: String? = null,
    var description: String? = null,
    var ownerId: String? = null,
    var sharedWith: Map<String, SharedUserInfo>? = null,
    var holidays: Map<String, HolidayModel>? = null
)
{
    constructor() : this("", null, null, null, null, null)
}

// ← ADD THIS NEW DATA CLASS
data class SharedUserInfo(
    var status: String? = "pending",  //Can be pending or accepted
    var canEdit: Boolean = false,
    var canShare: Boolean = false,
    var invitedAt: Long? = null,
    var acceptedAt: Long? = null
) {
    constructor() : this(null, false, false, null, null)
}
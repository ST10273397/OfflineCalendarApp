package com.example.prog7314progpoe.database.calendar

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.prog7314progpoe.database.holidays.HolidayModel

/**
 * Data class representing a Calendar in the Room database.
 * Each calendar can have a title, description, owner, shared users, and associated holidays.
 */
@Entity(tableName = "Calendars")
data class CalendarModel(
    @PrimaryKey
    var calendarId: String,                    // Unique ID for the calendar
    var title: String? = null,                // Optional title of the calendar
    var description: String? = null,          // Optional description
    var ownerId: String? = null,              // Owner's user ID
    var sharedWith: Map<String, SharedUserInfo>? = null, // Users with whom the calendar is shared
    var holidays: Map<String, HolidayModel>? = null,       // Holidays associated with this calendar
    var isMeetingCalendar: Boolean = false  // Identifies each users meetings calendar
) {
    // Default constructor for Room
    constructor() : this("", null, null, null, null, null)
}

/**
 * Represents the information for a user with whom a calendar is shared.
 * Tracks status, permissions, and timestamps for invite/accept actions.
 */
data class SharedUserInfo(
    var status: String? = "pending",  // "pending" or "accepted"
    var canEdit: Boolean = false,     // Permission to edit calendar
    var canShare: Boolean = false,    // Permission to share calendar with others
    var invitedAt: Long? = null,      // Timestamp when user was invited
    var acceptedAt: Long? = null      // Timestamp when user accepted the invite
) {
    // Default constructor for Room/serialization
    constructor() : this(null, false, false, null, null)
}

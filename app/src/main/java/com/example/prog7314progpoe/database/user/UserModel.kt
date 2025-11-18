package com.example.prog7314progpoe.database.user

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.prog7314progpoe.database.holidays.HolidayModel.DateInfo

/**
 * Represents a user stored in the local Room database.
 * Includes basic user info, authentication details, and metadata for offline login.
 */
@Entity(tableName = "Users")
data class UserModel(

    @PrimaryKey
    val userId: String,                  // Unique identifier (Firebase UID or local UUID)

    var email: String = "",              // User email (used for login)
    var firstName: String? = null,       // Optional first name
    var lastName: String? = null,        // Optional last name
    var password: String = "",           // Optional password for offline login

    @Embedded
    var dateOfBirth: DateInfo? = null,   // Optional date of birth

    var location: String? = null,        // Optional user location
    var isPrimary: Boolean = false,      // Marks this user as primary for device login
    val lastLoginAt: Long? = null        // Timestamp of the last login (epoch millis)
) {
    /**
     * Default constructor for Room and Firebase mapping.
     */
    constructor() : this(
        userId = "",
        email = "",
        firstName = "",
        lastName = null,
        password = "",
        dateOfBirth = null,
        location = null,
        isPrimary = false,
        lastLoginAt = null
    )
}

package com.example.prog7314progpoe.database.user

import android.R
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.prog7314progpoe.database.holidays.HolidayModel.DateInfo

@Entity(tableName = "Users")
data class UserModel(
    @PrimaryKey val userId: String? = null,
    var email: String? = null,
    var firstName: String? = null,
    var lastName: String? = null,
    var password: String? = null,
    var dateOfBirth: DateInfo? = null,
    var location: String? = null,
    var isPrimary: Boolean = false
) {
    constructor() : this(null, null, null, null, null, null, null, false)
}

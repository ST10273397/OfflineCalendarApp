package com.example.prog7314progpoe.database.user

import android.R
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.prog7314progpoe.database.holidays.HolidayModel.DateInfo

@Entity(tableName = "Users")
data class UserModel(
    @PrimaryKey val userId: String,
    var email: String = "",
    var firstName: String? = null,
    var lastName: String? = null,
    var password: String = "" ,
    @Embedded
    var dateOfBirth: DateInfo? = null,
    var location: String? = null,
    var isPrimary: Boolean = false,
    val lastLoginAt: Long? = null // Track last login time
) {
    constructor() : this("", "", "", null, "", null, null, false, null)
}

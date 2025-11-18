package com.example.prog7314progpoe.database.user

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserModel)

    @Query("SELECT * FROM users WHERE userId = :id")
    suspend fun getUserById(id: String): UserModel?

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserModel>

    @Query("SELECT * FROM users WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryUser(): UserModel?

    @Query("UPDATE users SET isPrimary = 0")
    suspend fun clearAllPrimaryFlags()

    @Delete
    suspend fun delete(user: UserModel)

    @Update
    suspend fun update(user: UserModel)

    // **NEW: Get user by email (for offline login)**
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserModel?

    // **NEW: Get all users sorted by last login (for user picker)**
    @Query("SELECT * FROM users ORDER BY lastLoginAt DESC")
    suspend fun getAllUsersSortedByLogin(): List<UserModel>

    // **NEW: Update last login timestamp**
    @Query("UPDATE users SET lastLoginAt = :timestamp WHERE userId = :userId")
    suspend fun updateLastLogin(userId: String, timestamp: Long)

    // **NEW: Count total users**
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    // **NEW: Delete all users**
    @Query("DELETE FROM users")
    suspend fun deleteAll()
}
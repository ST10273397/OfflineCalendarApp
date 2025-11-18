package com.example.prog7314progpoe.database.user

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Data Access Object (DAO) for managing UserModel entities in the local Room database.
 * Provides methods to insert, query, update, and delete user data, as well as support for offline login and user management.
 */
@Dao
interface UserDAO {

    // -------------------------------
    // Insert & Update
    // -------------------------------

    /**
     * Insert a new user into the database.
     * Replaces existing user if the ID already exists.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserModel)

    /**
     * Update an existing user's information.
     */
    @Update
    suspend fun update(user: UserModel)

    /**
     * Update the last login timestamp for a user.
     *
     * @param userId ID of the user
     * @param timestamp Epoch time in milliseconds of last login
     */
    @Query("UPDATE users SET lastLoginAt = :timestamp WHERE userId = :userId")
    suspend fun updateLastLogin(userId: String, timestamp: Long)

    // -------------------------------
    // Queries
    // -------------------------------

    /**
     * Retrieve a user by their unique ID.
     *
     * @param id User ID
     * @return UserModel or null if not found
     */
    @Query("SELECT * FROM users WHERE userId = :id")
    suspend fun getUserById(id: String): UserModel?

    /**
     * Retrieve a user by their email.
     * Useful for offline login.
     *
     * @param email User email
     * @return UserModel or null if not found
     */
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserModel?

    /**
     * Retrieve all users in the database.
     */
    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserModel>

    /**
     * Retrieve all users sorted by last login time (descending).
     * Useful for user picker UI.
     */
    @Query("SELECT * FROM users ORDER BY lastLoginAt DESC")
    suspend fun getAllUsersSortedByLogin(): List<UserModel>

    /**
     * Retrieve the primary user, if one is marked as primary.
     */
    @Query("SELECT * FROM users WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryUser(): UserModel?

    /**
     * Clear all primary user flags.
     * Useful when setting a new primary user.
     */
    @Query("UPDATE users SET isPrimary = 0")
    suspend fun clearAllPrimaryFlags()

    /**
     * Count the total number of users in the database.
     *
     * @return Total number of users
     */
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    // -------------------------------
    // Delete
    // -------------------------------

    /**
     * Delete a specific user from the database.
     */
    @Delete
    suspend fun delete(user: UserModel)

    /**
     * Delete all users from the database.
     */
    @Query("DELETE FROM users")
    suspend fun deleteAll()
}

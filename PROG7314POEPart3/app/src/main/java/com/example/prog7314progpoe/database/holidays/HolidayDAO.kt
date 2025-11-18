package com.example.prog7314progpoe.database.holidays

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Data Access Object (DAO) for managing HolidayModel entities in the database.
 * Provides methods to insert, update, delete, and query holiday data.
 */
@Dao
interface HolidayDAO {

    /**
     * Insert a single holiday into the database.
     * If a holiday with the same ID exists, it will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(holiday: HolidayModel)

    /**
     * Insert a list of holidays into the database.
     * Existing holidays with matching IDs will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(holidays: List<HolidayModel>)

    /**
     * Retrieve a holiday by its unique ID.
     * Returns null if no holiday is found.
     */
    @Query("SELECT * FROM holidays WHERE holidayId = :id")
    suspend fun getHolidayById(id: String): HolidayModel?

    /**
     * Retrieve all holidays stored in the database.
     */
    @Query("SELECT * FROM holidays")
    suspend fun getAllHolidays(): List<HolidayModel>

    /**
     * Delete a holiday by its unique ID.
     */
    @Query("DELETE FROM holidays WHERE holidayId = :id")
    suspend fun deleteById(id: String)

    /**
     * Delete all holidays from the database.
     */
    @Query("DELETE FROM holidays")
    suspend fun deleteAll()

    /**
     * Retrieve holidays by a specific source (e.g., calendar or country).
     *
     * @param sourceId The ID of the source.
     * @param sourceType The type of the source (e.g., "public", "custom").
     */
    @Query("SELECT * FROM holidays WHERE sourceId = :sourceId AND sourceType = :sourceType")
    suspend fun getHolidaysBySource(sourceId: String, sourceType: String): List<HolidayModel>

    /**
     * Get cached public holidays for a specific country.
     *
     * @param countryIso The ISO code of the country.
     */
    @Query("SELECT * FROM holidays WHERE sourceId = :countryIso AND sourceType = 'public'")
    suspend fun getPublicHolidays(countryIso: String): List<HolidayModel>

    /**
     * Clear cached holidays that are older than the given timestamp.
     *
     * @param timestamp Epoch time in milliseconds. All cached entries older than this will be deleted.
     */
    @Query("DELETE FROM holidays WHERE cachedAt < :timestamp")
    suspend fun clearOldCache(timestamp: Long)

    /**
     * Update an existing holiday.
     * If a holiday with the same ID exists, it will be replaced.
     */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(holiday: HolidayModel)

    /**
     * Delete a holiday using the HolidayModel object.
     */
    @Delete
    suspend fun delete(holiday: HolidayModel)
}

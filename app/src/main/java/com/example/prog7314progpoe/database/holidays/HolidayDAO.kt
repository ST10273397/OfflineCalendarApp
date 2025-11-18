package com.example.prog7314progpoe.database.holidays

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface HolidayDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(holiday: HolidayModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(holidays: List<HolidayModel>)

    @Query("SELECT * FROM holidays WHERE holidayId = :id")
    suspend fun getHolidayById(id: String): HolidayModel?

    @Query("SELECT * FROM holidays")
    suspend fun getAllHolidays(): List<HolidayModel>

    @Query("DELETE FROM holidays WHERE holidayId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM holidays")
    suspend fun deleteAll()

    // **NEW: Get holidays by source (calendar or country)**
    @Query("SELECT * FROM holidays WHERE sourceId = :sourceId AND sourceType = :sourceType")
    suspend fun getHolidaysBySource(sourceId: String, sourceType: String): List<HolidayModel>

    // **NEW: Get cached public holidays for a country**
    @Query("SELECT * FROM holidays WHERE sourceId = :countryIso AND sourceType = 'public'")
    suspend fun getPublicHolidays(countryIso: String): List<HolidayModel>

    // **NEW: Clear old cache (older than 30 days)**
    @Query("DELETE FROM holidays WHERE cachedAt < :timestamp")
    suspend fun clearOldCache(timestamp: Long)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(holiday: HolidayModel)

    @Delete
    suspend fun delete(holiday: HolidayModel)
}


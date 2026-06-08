package com.tom_crayon.lastseen.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for disconnect records.
 * Supports write, query, 90-day auto-cleanup, and manual single-record deletion.
 */
@Dao
interface DisconnectDao {

    /**
     * Insert a new disconnect record.
     * Uses REPLACE strategy as a safety net — duplicate timestamps for the same
     * device should overwrite rather than create duplicates.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DisconnectRecord): Long

    /**
     * Get all disconnect records ordered by most recent first.
     * Returns a Flow for reactive UI updates via Compose collectAsState().
     */
    @Query("SELECT * FROM disconnect_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<DisconnectRecord>>

    /**
     * Get a single record by its ID (for detail screen navigation).
     */
    @Query("SELECT * FROM disconnect_records WHERE id = :id")
    suspend fun getRecordById(id: Long): DisconnectRecord?

    /**
     * Get records for a specific device, ordered by most recent first.
     */
    @Query("SELECT * FROM disconnect_records WHERE deviceMac = :mac ORDER BY timestamp DESC")
    fun getRecordsByDevice(mac: String): Flow<List<DisconnectRecord>>

    /**
     * Get the most recent disconnect record (for map auto-focus).
     */
    @Query("SELECT * FROM disconnect_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRecord(): DisconnectRecord?

    /**
     * Delete a single record by ID (manual swipe-to-delete).
     */
    @Query("DELETE FROM disconnect_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Delete all records older than 90 days (7,776,000,000 ms).
     * Called periodically or on app startup for auto-cleanup.
     */
    @Query("DELETE FROM disconnect_records WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)

    /**
     * Get total record count (for UI empty state detection).
     */
    @Query("SELECT COUNT(*) FROM disconnect_records")
    fun getRecordCount(): Flow<Int>
}

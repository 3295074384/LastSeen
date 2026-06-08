package com.tom_crayon.lastseen.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for LastSeen app.
 * Stores all Bluetooth device disconnect location records.
 */
@Database(
    entities = [DisconnectRecord::class],
    version = 1,
    exportSchema = false
)
abstract class LastSeenDatabase : RoomDatabase() {
    abstract fun disconnectDao(): DisconnectDao
}

package com.midnight.kuira.core.indexer.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for UTXO storage.
 *
 * Stores unshielded UTXOs and dust tokens for local balance calculation.
 * Eventually will also store shielded UTXOs (future).
 */
@Database(
    entities = [
        UnshieldedUtxoEntity::class,
        DustTokenEntity::class
    ],
    version = 4,  // Bumped from 3 to 4 to add dust_tokens table
    exportSchema = false
)
abstract class UtxoDatabase : RoomDatabase() {
    /**
     * DAO for unshielded UTXO operations.
     */
    abstract fun unshieldedUtxoDao(): UnshieldedUtxoDao

    /**
     * DAO for dust token operations.
     */
    abstract fun dustDao(): DustDao

    companion object {
        private const val DATABASE_NAME = "kuira-utxo-database"

        @Volatile
        private var INSTANCE: UtxoDatabase? = null

        /**
         * Get database instance (singleton).
         *
         * Thread-safe lazy initialization.
         */
        fun getInstance(context: Context): UtxoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): UtxoDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                UtxoDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration() // TODO: Add proper migrations for production
                .enableMultiInstanceInvalidation() // Allow multiple processes to share database
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // Enable WAL for better concurrency
                .build()
        }

        /**
         * Create in-memory database for testing.
         *
         * Data is lost when database is closed.
         */
        fun createInMemoryDatabase(context: Context): UtxoDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                UtxoDatabase::class.java
            )
                .allowMainThreadQueries() // Only for testing!
                .build()
        }
    }
}
